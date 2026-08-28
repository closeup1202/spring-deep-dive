package com.exam.flyway.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway 를 쓰는 팀의 최소 안전장치.
 *
 * <p>완전히 빈 DB 에 V1 부터 끝까지 재생해서
 * <ol>
 *   <li>모든 마이그레이션이 실제로 실행 가능한지</li>
 *   <li>그 결과 스키마가 JPA 엔티티와 일치하는지 (ddl-auto=validate)</li>
 * </ol>
 * 를 검증한다. 이 테스트가 깨지면 운영 배포도 깨진다.
 *
 * <p>로컬 DB 에서는 이미 적용된 마이그레이션이 다시 돌지 않기 때문에,
 * 새로 짠 SQL 이 실제로 실행 가능한지는 이 테스트만이 보장한다.
 *
 * <p>prod 프로필로 돌려서 seed 데이터 없이 스키마만 검증한다.
 */
@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers
@DisplayName("빈 DB 에 마이그레이션 전체를 재생한다")
class MigrationSmokeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("모든 마이그레이션이 성공 상태로 기록된다")
    void allMigrationsSucceed() {
        List<String> failed = jdbc().queryForList(
                "select script from flyway_schema_history where success = false", String.class);

        assertThat(failed).isEmpty();
    }

    @Test
    @DisplayName("이력에 baseline 이 아닌 versioned 마이그레이션이 남아 있다")
    void versionedMigrationsApplied() {
        List<String> versions = jdbc().queryForList(
                "select version from flyway_schema_history "
                        + "where type <> 'BASELINE' and version is not null "
                        + "order by installed_rank", String.class);

        assertThat(versions).contains("1", "2", "3", "4");
    }

    @Test
    @DisplayName("Repeatable 로 만든 뷰가 조회 가능하다")
    void repeatableViewExists() {
        Integer count = jdbc().queryForObject("select count(*) from member_summary", Integer.class);

        assertThat(count).isNotNull();
    }

    @Test
    @DisplayName("prod 프로필에서는 seed 데이터가 들어가지 않는다")
    void seedIsNotAppliedOnProd() {
        Integer members = jdbc().queryForObject("select count(*) from member", Integer.class);

        assertThat(members).isZero();
    }
}
