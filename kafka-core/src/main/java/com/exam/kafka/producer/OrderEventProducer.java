package com.exam.kafka.producer;

import com.exam.kafka.domain.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private static final String TOPIC = "order-events";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 비동기 전송 — 처리량 우선.
     *
     * orderId를 key로 사용:
     *   → 같은 orderId는 항상 같은 파티션으로 → 파티션 내 순서 보장
     *   → CREATED → PAID → SHIPPED 이벤트 순서가 유지됨
     *
     * whenComplete 콜백:
     *   → 성공/실패를 Sender Thread에서 비동기 처리
     *   → 호출 스레드는 전송 결과를 기다리지 않음 (throughput 향상)
     */
    public void sendAsync(OrderEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Producer] 비동기 전송 실패: orderId={}, cause={}",
                        event.orderId(), ex.getMessage());
                    // 실패 처리: Outbox 패턴으로 재발행, 알림 전송 등
                } else {
                    RecordMetadata meta = result.getRecordMetadata();
                    log.info("[Producer] 비동기 전송 성공: orderId={}, partition={}, offset={}, timestamp={}",
                        event.orderId(), meta.partition(), meta.offset(), meta.timestamp());
                }
            });
    }

    /**
     * 동기 전송 — 결제 완료처럼 전송 보장이 중요한 경우.
     *
     * .get(timeout)으로 브로커 응답을 기다린 후 반환.
     * timeout 내 응답 없으면 TimeoutException → 상위에서 보상 처리(Outbox 등).
     *
     * 주의: 동기 전송은 호출 스레드를 블로킹 → 처리량이 크게 감소.
     *   → 반드시 필요한 경우에만 사용 (결제 확인, 송금 완료 등)
     */
    public SendResult<String, Object> sendSync(OrderEvent event) {
        try {
            SendResult<String, Object> result =
                kafkaTemplate.send(TOPIC, event.orderId(), event)
                    .get(10, TimeUnit.SECONDS); // 10초 타임아웃

            RecordMetadata meta = result.getRecordMetadata();
            log.info("[Producer] 동기 전송 성공: orderId={}, partition={}, offset={}",
                event.orderId(), meta.partition(), meta.offset());
            return result;

        } catch (TimeoutException e) {
            log.error("[Producer] 전송 타임아웃: orderId={}", event.orderId());
            throw new RuntimeException("Kafka 전송 타임아웃", e);
        } catch (ExecutionException e) {
            log.error("[Producer] 전송 실패: orderId={}, cause={}", event.orderId(), e.getCause().getMessage());
            throw new RuntimeException("Kafka 전송 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("전송 중 인터럽트", e);
        }
    }

    /**
     * 재시도용 토픽으로 전송 (@RetryableTopic 소비자가 소비).
     */
    public void sendToRetryableTopic(OrderEvent event) {
        kafkaTemplate.send("order-events-retryable", event.orderId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Producer] retryable 토픽 전송 실패: {}", ex.getMessage());
                } else {
                    log.info("[Producer] retryable 토픽 전송 성공: orderId={}", event.orderId());
                }
            });
    }
}
