package com.project.curve.spring.outbox.persistence.jdbc;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.outbox.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of OutboxEventRepository.
 * <p>
 * Implements the Outbox pattern using JdbcTemplate without JPA.
 * Handles SQL dialects to support various databases (MySQL, PostgreSQL, Oracle, H2, etc.).
 */
@Slf4j
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DbType dbType;

    private enum DbType {
        MYSQL, POSTGRESQL, ORACLE, H2, SQL_SERVER, OTHER
    }

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbType = resolveDbType(dataSource);
        log.info("JdbcOutboxEventRepository initialized with DB type: {}", this.dbType);
    }

    private DbType resolveDbType(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (productName.contains("mysql") || productName.contains("mariadb")) {
                return DbType.MYSQL;
            } else if (productName.contains("postgresql")) {
                return DbType.POSTGRESQL;
            } else if (productName.contains("oracle")) {
                return DbType.ORACLE;
            } else if (productName.contains("h2")) {
                return DbType.H2;
            } else if (productName.contains("sql server") || productName.contains("microsoft")) {
                return DbType.SQL_SERVER;
            }
        } catch (SQLException e) {
            log.warn("Failed to determine database product name", e);
        }
        return DbType.OTHER;
    }

    private static final RowMapper<OutboxEvent> ROW_MAPPER = (rs, rowNum) -> {
        String eventId = rs.getString("event_id");
        String aggregateType = rs.getString("aggregate_type");
        String aggregateId = rs.getString("aggregate_id");
        String eventType = rs.getString("event_type");
        String payload = rs.getString("payload");
        Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
        OutboxStatus status = OutboxStatus.valueOf(rs.getString("status"));
        int retryCount = rs.getInt("retry_count");

        Timestamp publishedAtTs = rs.getTimestamp("published_at");
        Instant publishedAt = publishedAtTs != null ? publishedAtTs.toInstant() : null;

        String errorMessage = rs.getString("error_message");

        Timestamp nextRetryAtTs = rs.getTimestamp("next_retry_at");
        Instant nextRetryAt = nextRetryAtTs != null ? nextRetryAtTs.toInstant() : null;

        return OutboxEvent.restore(
                eventId, aggregateType, aggregateId, eventType, payload, occurredAt,
                status, retryCount, publishedAt, errorMessage, nextRetryAt
        );
    };

    @Override
    @Transactional
    public void save(OutboxEvent event) {
        // 1. Try UPDATE first
        int updated = jdbcTemplate.update("""
                        UPDATE curve_outbox_events SET
                            status = ?, retry_count = ?, published_at = ?, error_message = ?, next_retry_at = ?, updated_at = ?
                        WHERE event_id = ?
                        """,
                event.getStatus().name(),
                event.getRetryCount(),
                event.getPublishedAt() != null ? Timestamp.from(event.getPublishedAt()) : null,
                event.getErrorMessage(),
                event.getNextRetryAt() != null ? Timestamp.from(event.getNextRetryAt()) : null,
                Timestamp.from(Instant.now()),
                event.getEventId()
        );

        // 2. Try INSERT if UPDATE failed
        if (updated == 0) {
            try {
                jdbcTemplate.update("""
                                INSERT INTO curve_outbox_events (
                                    event_id, aggregate_type, aggregate_id, event_type, payload, occurred_at,
                                    status, retry_count, published_at, error_message, next_retry_at, created_at, updated_at
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        event.getEventId(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getEventType(),
                        event.getPayload(),
                        Timestamp.from(event.getOccurredAt()),
                        event.getStatus().name(),
                        event.getRetryCount(),
                        event.getPublishedAt() != null ? Timestamp.from(event.getPublishedAt()) : null,
                        event.getErrorMessage(),
                        event.getNextRetryAt() != null ? Timestamp.from(event.getNextRetryAt()) : null,
                        Timestamp.from(Instant.now()),
                        Timestamp.from(Instant.now())
                );
            } catch (DuplicateKeyException e) {
                log.debug("Concurrent insert detected for eventId={}, retrying update.", event.getEventId());
                jdbcTemplate.update("""
                                UPDATE curve_outbox_events SET
                                    status = ?, retry_count = ?, published_at = ?, error_message = ?, next_retry_at = ?, updated_at = ?
                                WHERE event_id = ?
                                """,
                        event.getStatus().name(),
                        event.getRetryCount(),
                        event.getPublishedAt() != null ? Timestamp.from(event.getPublishedAt()) : null,
                        event.getErrorMessage(),
                        event.getNextRetryAt() != null ? Timestamp.from(event.getNextRetryAt()) : null,
                        Timestamp.from(Instant.now()),
                        event.getEventId()
                );
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxEvent> findById(String eventId) {
        try {
            OutboxEvent event = jdbcTemplate.queryForObject(
                    "SELECT * FROM curve_outbox_events WHERE event_id = ?",
                    ROW_MAPPER,
                    eventId
            );
            return Optional.ofNullable(event);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findByStatus(OutboxStatus status, int limit) {
        String sql = buildLimitQuery("SELECT * FROM curve_outbox_events WHERE status = ? ORDER BY occurred_at ASC", limit);
        return jdbcTemplate.query(sql, ROW_MAPPER, status.name());
    }

    @Override
    @Transactional
    public List<OutboxEvent> findPendingForProcessing(int limit) {
        String baseSql = "SELECT * FROM curve_outbox_events WHERE status = ? AND next_retry_at <= ? ORDER BY occurred_at ASC";
        String sql = buildLimitQuery(baseSql, limit);

        // Add SKIP LOCKED
        if (dbType == DbType.MYSQL || dbType == DbType.POSTGRESQL || dbType == DbType.ORACLE) {
            sql += " FOR UPDATE SKIP LOCKED";
        } else if (dbType == DbType.SQL_SERVER) {
            // SQL Server uses hint syntax (WITH (READPAST, UPDLOCK))
            // But the structure is complex as hints need to be in the SELECT clause.
            // For simplicity, skip here or needs future enhancement.
            // Proceed without SKIP LOCKED for now (potential concurrency issues)
        }

        try {
            return jdbcTemplate.query(sql, ROW_MAPPER, OutboxStatus.PENDING.name(), Timestamp.from(Instant.now()));
        } catch (Exception e) {
            log.warn("SKIP LOCKED query failed. Falling back to normal select. Error: {}", e.getMessage());
            return findByStatus(OutboxStatus.PENDING, limit);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId) {
        return jdbcTemplate.query(
                "SELECT * FROM curve_outbox_events WHERE aggregate_type = ? AND aggregate_id = ? ORDER BY occurred_at ASC",
                ROW_MAPPER,
                aggregateType,
                aggregateId
        );
    }

    @Override
    @Transactional
    public void deleteById(String eventId) {
        jdbcTemplate.update("DELETE FROM curve_outbox_events WHERE event_id = ?", eventId);
    }

    @Override
    @Transactional
    public int deleteByStatusAndOccurredAtBefore(OutboxStatus status, Instant before, int limit) {
        // Use query ID then delete approach (safest)
        String selectSql = buildLimitQuery("SELECT event_id FROM curve_outbox_events WHERE status = ? AND occurred_at < ?", limit);

        List<String> ids = jdbcTemplate.query(
                selectSql,
                (rs, rowNum) -> rs.getString("event_id"),
                status.name(),
                Timestamp.from(before)
        );

        if (ids.isEmpty()) {
            return 0;
        }

        String inSql = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.update(
                String.format("DELETE FROM curve_outbox_events WHERE event_id IN (%s)", inSql),
                ids.toArray()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM curve_outbox_events", Long.class);
        return count != null ? count : 0;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OutboxStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM curve_outbox_events WHERE status = ?",
                Long.class,
                status.name()
        );
        return count != null ? count : 0;
    }

    private String buildLimitQuery(String sql, int limit) {
        if (dbType == DbType.ORACLE) {
            return sql + " FETCH FIRST " + limit + " ROWS ONLY";
        } else if (dbType == DbType.SQL_SERVER) {
            // SQL Server uses SELECT TOP N ... format, requires string manipulation
            return sql.replaceFirst("SELECT", "SELECT TOP " + limit);
        } else {
            // MySQL, PostgreSQL, H2, Others
            return sql + " LIMIT " + limit;
        }
    }
}
