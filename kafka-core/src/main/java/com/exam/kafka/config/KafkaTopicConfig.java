package com.exam.kafka.config;

import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration
public class KafkaTopicConfig {

    /**
     * 주문 이벤트 메인 토픽.
     * - 파티션 3개: 소비자 3개까지 병렬 처리 가능 (파티션 수 = 최대 병렬 소비자 수)
     * - retention.ms: 7일 보존 (소비자 지연 복구 여유)
     * - retention.bytes: 파티션당 1GB 초과 시 오래된 세그먼트부터 삭제
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
            .partitions(3)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG,
                String.valueOf(7 * 24 * 60 * 60 * 1000L))       // 7일
            .config(TopicConfig.RETENTION_BYTES_CONFIG,
                String.valueOf(1024L * 1024 * 1024))             // 1GB per partition
            .config(TopicConfig.SEGMENT_BYTES_CONFIG,
                String.valueOf(256 * 1024 * 1024))               // 세그먼트 최대 256MB
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
            .build();
    }

    /**
     * @RetryableTopic이 사용하는 재시도 토픽들은 Spring Kafka가 자동 생성.
     * 직접 생성이 필요한 것은 DLT만.
     *
     * DLT(Dead Letter Topic):
     * - 모든 재시도 소진 후 도달하는 최종 실패 토픽
     * - 30일 보존: 수동 확인 및 재처리 시간 확보
     * - 운영팀 알림, 수동 replay 용도
     */
    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name("order-events.DLT")
            .partitions(3)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG,
                String.valueOf(30 * 24 * 60 * 60 * 1000L))       // 30일
            .build();
    }

    /**
     * @RetryableTopic 전용 토픽.
     * Spring Kafka가 자동 생성하지만 명시적 선언으로 설정 제어.
     */
    @Bean
    public NewTopic orderEventsRetryableTopic() {
        return TopicBuilder.name("order-events-retryable")
            .partitions(3)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG,
                String.valueOf(7 * 24 * 60 * 60 * 1000L))
            .build();
    }
}
