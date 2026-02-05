package com.project.curve.core.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxEvent 테스트")
class OutboxEventTest {

    @Test
    @DisplayName("정상적인 OutboxEvent 생성")
    void createValidOutboxEvent() {
        // given
        String eventId = "evt-123";
        String aggregateType = "Order";
        String aggregateId = "order-123";
        String eventType = "ORDER_CREATED";
        String payload = "{\"orderId\":\"order-123\"}";
        Instant occurredAt = Instant.now();

        // when
        OutboxEvent event = new OutboxEvent(
                eventId, aggregateType, aggregateId, eventType, payload, occurredAt
        );

        // then
        assertNotNull(event);
        assertEquals(eventId, event.getEventId());
        assertEquals(aggregateType, event.getAggregateType());
        assertEquals(aggregateId, event.getAggregateId());
        assertEquals(eventType, event.getEventType());
        assertEquals(payload, event.getPayload());
        assertEquals(occurredAt, event.getOccurredAt());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertNull(event.getPublishedAt());
        assertNull(event.getErrorMessage());
        assertNotNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("eventId가 null이면 예외 발생")
    void createOutboxEventWithNullEventId_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent(null, "Order", "order-123", "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("eventId가 빈 문자열이면 예외 발생")
    void createOutboxEventWithBlankEventId_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("", "Order", "order-123", "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("aggregateType이 null이면 예외 발생")
    void createOutboxEventWithNullAggregateType_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", null, "order-123", "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("aggregateType must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("aggregateId가 null이면 예외 발생")
    void createOutboxEventWithNullAggregateId_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", null, "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("aggregateId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("eventType이 null이면 예외 발생")
    void createOutboxEventWithNullEventType_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", "order-123", null,
                        "{}", Instant.now())
        );
        assertEquals("eventType must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("payload가 null이면 예외 발생")
    void createOutboxEventWithNullPayload_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", "order-123", "ORDER_CREATED",
                        null, Instant.now())
        );
        assertEquals("payload must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("occurredAt이 null이면 예외 발생")
    void createOutboxEventWithNullOccurredAt_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", "order-123", "ORDER_CREATED",
                        "{}", null)
        );
        assertEquals("occurredAt must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("markAsPublished 테스트")
    void testMarkAsPublished() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        event.markAsPublished();

        // then
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getErrorMessage());
        assertNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("markAsFailed 테스트")
    void testMarkAsFailed() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        String errorMessage = "Kafka connection failed";

        // when
        event.markAsFailed(errorMessage);

        // then
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(errorMessage, event.getErrorMessage());
        assertNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("scheduleNextRetry 테스트")
    void testScheduleNextRetry() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        long backoffMs = 5000L;

        // when
        int retryCount = event.scheduleNextRetry(backoffMs);

        // then
        assertEquals(1, retryCount);
        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getNextRetryAt());
        assertTrue(event.getNextRetryAt().isAfter(Instant.now().minusSeconds(1)));
    }

    @Test
    @DisplayName("scheduleNextRetry 여러 번 호출 테스트")
    void testScheduleNextRetryMultipleTimes() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(2000L);
        int retryCount = event.scheduleNextRetry(3000L);

        // then
        assertEquals(3, retryCount);
        assertEquals(3, event.getRetryCount());
    }

    @Test
    @DisplayName("exceededMaxRetries 테스트 - 초과하지 않음")
    void testExceededMaxRetries_notExceeded() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(1000L);

        // when
        boolean exceeded = event.exceededMaxRetries(3);

        // then
        assertFalse(exceeded);
    }

    @Test
    @DisplayName("exceededMaxRetries 테스트 - 초과함")
    void testExceededMaxRetries_exceeded() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(1000L);

        // when
        boolean exceeded = event.exceededMaxRetries(3);

        // then
        assertTrue(exceeded);
    }

    @Test
    @DisplayName("canPublish 테스트 - PENDING 상태")
    void testCanPublish_pending() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        boolean canPublish = event.canPublish();

        // then
        assertTrue(canPublish);
    }

    @Test
    @DisplayName("canPublish 테스트 - PUBLISHED 상태")
    void testCanPublish_published() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.markAsPublished();

        // when
        boolean canPublish = event.canPublish();

        // then
        assertFalse(canPublish);
    }

    @Test
    @DisplayName("canPublish 테스트 - FAILED 상태")
    void testCanPublish_failed() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.markAsFailed("Error");

        // when
        boolean canPublish = event.canPublish();

        // then
        assertFalse(canPublish);
    }

    @Test
    @DisplayName("restore 메서드 테스트")
    void testRestore() {
        // given
        String eventId = "evt-123";
        String aggregateType = "Order";
        String aggregateId = "order-123";
        String eventType = "ORDER_CREATED";
        String payload = "{}";
        Instant occurredAt = Instant.now();
        OutboxStatus status = OutboxStatus.PUBLISHED;
        int retryCount = 2;
        Instant publishedAt = Instant.now();
        String errorMessage = "Some error";
        Instant nextRetryAt = Instant.now().plusSeconds(10);

        // when
        OutboxEvent event = OutboxEvent.restore(
                eventId, aggregateType, aggregateId, eventType, payload,
                occurredAt, status, retryCount, publishedAt, errorMessage, nextRetryAt
        );

        // then
        assertEquals(eventId, event.getEventId());
        assertEquals(aggregateType, event.getAggregateType());
        assertEquals(aggregateId, event.getAggregateId());
        assertEquals(eventType, event.getEventType());
        assertEquals(payload, event.getPayload());
        assertEquals(occurredAt, event.getOccurredAt());
        assertEquals(status, event.getStatus());
        assertEquals(retryCount, event.getRetryCount());
        assertEquals(publishedAt, event.getPublishedAt());
        assertEquals(errorMessage, event.getErrorMessage());
        assertEquals(nextRetryAt, event.getNextRetryAt());
    }

    @Test
    @DisplayName("restore 메서드 테스트 - nextRetryAt이 null")
    void testRestore_withNullNextRetryAt() {
        // given
        Instant occurredAt = Instant.now();

        // when
        OutboxEvent event = OutboxEvent.restore(
                "evt-123", "Order", "order-123", "ORDER_CREATED", "{}",
                occurredAt, OutboxStatus.PENDING, 0, null, null, null
        );

        // then
        assertEquals(occurredAt, event.getNextRetryAt());
    }

    @Test
    @DisplayName("toString 테스트")
    void testToString() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        String toString = event.toString();

        // then
        assertNotNull(toString);
        assertTrue(toString.contains("evt-123"));
        assertTrue(toString.contains("Order"));
        assertTrue(toString.contains("order-123"));
        assertTrue(toString.contains("ORDER_CREATED"));
        assertTrue(toString.contains("PENDING"));
    }
}
