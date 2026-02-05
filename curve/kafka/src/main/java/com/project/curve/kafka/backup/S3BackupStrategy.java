package com.project.curve.kafka.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * S3 (or MinIO) implementation of EventBackupStrategy.
 * <p>
 * Backs up failed events to an S3 bucket.
 * Useful for Kubernetes environments where local file systems are ephemeral.
 */
@Slf4j
public class S3BackupStrategy implements EventBackupStrategy {

    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd")
            .withZone(ZoneId.of("UTC"));

    public S3BackupStrategy(S3Client s3Client, String bucketName, String prefix, ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.prefix = prefix != null ? prefix : "dlq-backup";
        this.objectMapper = objectMapper;
    }

    @Override
    public void backup(String eventId, Object payload, Throwable cause) {
        try {
            String content = serializeContent(payload);
            String key = generateKey(eventId);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/json")
                    .metadata(java.util.Map.of(
                            "eventId", eventId,
                            "cause", cause.getClass().getName(),
                            "timestamp", String.valueOf(System.currentTimeMillis())
                    ))
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromString(content, StandardCharsets.UTF_8));
            log.info("Event backed up to S3: bucket={}, key={}", bucketName, key);

        } catch (Exception e) {
            log.error("Failed to backup event to S3: eventId={}, bucket={}", eventId, bucketName, e);
            log.error("Event permanently lost (S3 backup failed). eventId={}", eventId);
        }
    }

    private String generateKey(String eventId) {
        String datePath = DATE_FORMATTER.format(Instant.now());
        // Structure: prefix/yyyy/MM/dd/eventId.json
        return String.format("%s/%s/%s.json", prefix, datePath, eventId);
    }

    private String serializeContent(Object originalValue) {
        if (originalValue instanceof String) {
            return (String) originalValue;
        }
        try {
            return objectMapper.writeValueAsString(originalValue);
        } catch (Exception e) {
            log.warn("Failed to serialize value as JSON for S3 backup, using toString()", e);
            return String.valueOf(originalValue);
        }
    }
}
