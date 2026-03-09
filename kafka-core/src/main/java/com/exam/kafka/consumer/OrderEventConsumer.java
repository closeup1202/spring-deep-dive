package com.exam.kafka.consumer;

import com.exam.kafka.domain.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class OrderEventConsumer {

    /**
     * 단건 처리 컨슈머 — Manual Commit.
     *
     * ContainerProperties.AckMode.MANUAL_IMMEDIATE 설정 시:
     *   ack.acknowledge() 호출 즉시 브로커에 오프셋 커밋.
     *
     * 중요: ack.acknowledge()는 반드시 처리 성공 후에만 호출.
     *   실패 시 호출하지 않으면 DefaultErrorHandler가 재시도 처리.
     *   실패 시 호출해버리면 오프셋이 커밋되어 메시지 유실 발생.
     */
    @KafkaListener(
        topics = "order-events",
        groupId = "order-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) {
        log.info("[Consumer] 수신: topic={}, partition={}, offset={}, key={}",
            record.topic(), record.partition(), record.offset(), record.key());

        processOrder(record.value());

        // 처리 성공 후 커밋
        // 실패(예외) 시 이 라인에 도달하지 않음 → DefaultErrorHandler 재시도
        ack.acknowledge();
    }

    /**
     * 배치 처리 컨슈머 — 한 번에 여러 메시지를 묶어서 처리.
     * <p>
     * 배치 처리 장점:
     *   - DB bulk insert / batch API 호출 가능
     *   - 개별 처리 대비 I/O 횟수 감소
     * <p>
     * containerFactory의 setBatchListener(true) 필요.
     * 여기서는 별도 배치 전용 팩토리를 직접 선언하지 않고 주석으로 설명.
     * <p>
     * 실제 배치 처리 시:
     * @KafkaListener(... containerFactory = "batchKafkaListenerContainerFactory")
     * public void consumeBatch(List<ConsumerRecord<String, OrderEvent>> records, Acknowledgment ack) {
     *     // records 전체를 DB에 bulk insert
     *     ack.acknowledge(); // 배치 전체 커밋
     * }
     */
    private void processOrder(OrderEvent event) {
        log.info("[Consumer] 처리: orderId={}, status={}, amount={}",
            event.orderId(), event.status(), event.amount());
        // 실제 비즈니스 로직: DB 저장, 재고 차감, 알림 발송 등
    }
}
