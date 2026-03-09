package com.exam.kafka.consumer;

import com.exam.kafka.domain.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RetryableOrderConsumer {

    /**
     * @RetryableTopic — 논블로킹(Non-Blocking) 재시도.
     * <p>
     * ─── 블로킹 재시도(DefaultErrorHandler) vs 논블로킹 재시도(@RetryableTopic) ───
     * <p>
     * 블로킹(DefaultErrorHandler):
     *   처리 실패 → 컨슈머 스레드 내에서 대기 후 재시도
     *   → 재시도 대기 중 해당 파티션의 다른 메시지 처리 불가
     *   → max.poll.interval.ms 초과 위험 (대기 시간 길면)
     *   → 짧은 재시도(수초 이내)에 적합
     * <p>
     * 논블로킹(@RetryableTopic):
     *   처리 실패 → 메시지를 재시도 토픽으로 발행 → 원본 파티션은 계속 소비
     *   → 재시도 토픽에서 지연 후 재처리 (별도 컨슈머 그룹)
     *   → 수분~수십분의 긴 재시도 간격에 적합
     *   → 원본 토픽 소비 지연 없음
     * <p>
     * ─── 재시도 토픽 구조 ───────────────────────────────────────────────
     * order-events-retryable
     *   ├─ order-events-retryable-retry-0 (2초 대기)
     *   ├─ order-events-retryable-retry-1 (4초 대기)
     *   ├─ order-events-retryable-retry-2 (8초 대기)
     *   └─ order-events-retryable.DLT     (최종 실패)
     * <p>
     * attempts = "4": 최초 1회 + 재시도 3회 = 총 4회 시도
     * <p>
     * ─── 지수 백오프 ───────────────────────────────────────────────────
     * @Backoff(value=2000, multiplier=2.0, maxDelay=10000):
     *   1차 재시도: 2초 대기
     *   2차 재시도: 4초 대기
     *   3차 재시도: 8초 대기 (10초 미만이므로 maxDelay 미적용)
     *   → thundering herd 방지: 동시 실패한 여러 메시지가 분산된 타이밍에 재시도
     */
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(
            value = 2_000L,       // 초기 대기 2초
            multiplier = 2.0,     // 재시도마다 2배 증가
            maxDelay = 10_000L    // 최대 10초
        ),
        dltTopicSuffix = ".DLT",
        dltStrategy = DltStrategy.FAIL_ON_ERROR,               // DLT 전송 실패 시 예외
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        autoCreateTopics = "true",
        numPartitions = "3",
        replicationFactor = "1"
    )
    @KafkaListener(topics = "order-events-retryable", groupId = "order-retryable-group")
    public void consume(ConsumerRecord<String, OrderEvent> record) {
        String currentTopic = record.topic();
        int attempt = extractAttemptNumber(currentTopic);

        log.info("[RetryableConsumer] 처리 시도 #{}: topic={}, offset={}, orderId={}",
            attempt, currentTopic, record.offset(), record.key());

        processWithPossibleFailure(record.value());

        log.info("[RetryableConsumer] 처리 성공: orderId={}", record.key());
    }

    /**
     * DLT 핸들러 — 모든 재시도 소진 후 최종 실패한 메시지 처리.
     * <p>
     * 실무 대응:
     *   1. 실패 메시지를 DB에 기록 (수동 처리 대기열)
     *   2. Slack/PagerDuty 알림
     *   3. 관리자 대시보드에 노출
     * <p>
     * DLT 메시지에는 원본 토픽, 파티션, 오프셋, 예외 정보가 헤더로 포함됨.
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, OrderEvent> record) {
        log.error("[DLT] 최종 실패 — 수동 처리 필요: topic={}, partition={}, offset={}, orderId={}",
            record.topic(), record.partition(), record.offset(), record.key());

        if (record.value() != null) {
            log.error("[DLT] 이벤트 상세: orderId={}, status={}, amount={}",
                record.value().orderId(), record.value().status(), record.value().amount());
        }

        // 실무: DB 저장, 알림 전송
        // failedEventRepository.save(FailedEvent.from(record));
        // alertService.sendDltAlert(record);
    }

    private void processWithPossibleFailure(OrderEvent event) {
        // FAILED 상태 이벤트는 처리 실패 시뮬레이션
        if (event.status() == OrderEvent.OrderStatus.FAILED) {
            throw new RuntimeException("주문 처리 실패 (시뮬레이션): orderId=" + event.orderId());
        }
        log.info("[RetryableConsumer] 처리 완료: orderId={}", event.orderId());
    }

    /** 재시도 토픽명에서 시도 횟수 추출 */
    private int extractAttemptNumber(String topic) {
        if (topic.contains("-retry-")) {
            String suffix = topic.substring(topic.lastIndexOf("-retry-") + 7);
            try {
                return Integer.parseInt(suffix) + 2; // 0-indexed → 2차 시도부터
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }
}
