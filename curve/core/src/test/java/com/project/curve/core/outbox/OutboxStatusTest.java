package com.project.curve.core.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxStatus 테스트")
class OutboxStatusTest {

    @Test
    @DisplayName("OutboxStatus enum 값 확인")
    void testOutboxStatusValues() {
        // when
        OutboxStatus[] statuses = OutboxStatus.values();

        // then
        assertEquals(3, statuses.length);
        assertEquals(OutboxStatus.PENDING, statuses[0]);
        assertEquals(OutboxStatus.PUBLISHED, statuses[1]);
        assertEquals(OutboxStatus.FAILED, statuses[2]);
    }

    @Test
    @DisplayName("OutboxStatus valueOf 테스트")
    void testValueOf() {
        // when & then
        assertEquals(OutboxStatus.PENDING, OutboxStatus.valueOf("PENDING"));
        assertEquals(OutboxStatus.PUBLISHED, OutboxStatus.valueOf("PUBLISHED"));
        assertEquals(OutboxStatus.FAILED, OutboxStatus.valueOf("FAILED"));
    }

    @Test
    @DisplayName("OutboxStatus 비교 테스트")
    void testComparison() {
        // given
        OutboxStatus pending = OutboxStatus.PENDING;
        OutboxStatus published = OutboxStatus.PUBLISHED;
        OutboxStatus failed = OutboxStatus.FAILED;

        // then
        assertNotEquals(pending, published);
        assertNotEquals(pending, failed);
        assertNotEquals(published, failed);
        assertEquals(pending, OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("OutboxStatus toString 테스트")
    void testToString() {
        // then
        assertEquals("PENDING", OutboxStatus.PENDING.toString());
        assertEquals("PUBLISHED", OutboxStatus.PUBLISHED.toString());
        assertEquals("FAILED", OutboxStatus.FAILED.toString());
    }

    @Test
    @DisplayName("OutboxStatus name 메서드 테스트")
    void testName() {
        // then
        assertEquals("PENDING", OutboxStatus.PENDING.name());
        assertEquals("PUBLISHED", OutboxStatus.PUBLISHED.name());
        assertEquals("FAILED", OutboxStatus.FAILED.name());
    }

    @Test
    @DisplayName("OutboxStatus ordinal 테스트")
    void testOrdinal() {
        // then
        assertEquals(0, OutboxStatus.PENDING.ordinal());
        assertEquals(1, OutboxStatus.PUBLISHED.ordinal());
        assertEquals(2, OutboxStatus.FAILED.ordinal());
    }

    @Test
    @DisplayName("잘못된 값으로 valueOf 호출 시 예외 발생")
    void testInvalidValueOf() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                OutboxStatus.valueOf("INVALID_STATUS")
        );
    }
}
