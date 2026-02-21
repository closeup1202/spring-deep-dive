package com.example.outbox.service;

import com.example.outbox.domain.OutboxEvent;
import com.example.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 아웃박스 테이블을 주기적으로 폴링하여 미전송 이벤트를 Kafka로 발행
     *
     * 주의사항:
     * 1. 폴링 주기는 시스템 특성에 맞게 조정 (현재 5초)
     * 2. 대량의 이벤트가 있을 경우 배치 처리 고려
     * 3. 실패한 이벤트에 대한 재시도 전략 필요
     */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEvent.EventStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Processing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Kafka로 이벤트 발행
                String topic = event.getEventType();
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());

                // 상태를 PROCESSED로 업데이트
                event.markAsProcessed();
                outboxEventRepository.save(event);

                log.info("Successfully published event {} to Kafka topic {}",
                        event.getId(), topic);
            } catch (Exception e) {
                log.error("Failed to publish event {} to Kafka", event.getId(), e);
                event.markAsFailed();
                outboxEventRepository.save(event);
            }
        }
    }

    /**
     * PROCESSED 상태의 오래된 이벤트 정리 (선택사항)
     * 운영 환경에서는 아웃박스 테이블이 계속 커질 수 있으므로 주기적으로 정리 필요
     */
    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시 실행
    @Transactional
    public void cleanupProcessedEvents() {
        // 예: 7일 이상 된 PROCESSED 이벤트 삭제
        log.info("Cleanup job for processed outbox events (not implemented)");
        // outboxEventRepository.deleteOldProcessedEvents(LocalDateTime.now().minusDays(7));
    }
}
