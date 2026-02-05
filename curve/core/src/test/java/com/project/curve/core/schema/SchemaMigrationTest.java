package com.project.curve.core.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaMigration 테스트")
class SchemaMigrationTest {

    static class PayloadV1 {
        String orderId;
        int amount;
    }

    static class PayloadV2 {
        String orderId;
        int amount;
        String status;
    }

    @Test
    @DisplayName("SchemaMigration 구현 테스트")
    void testSchemaMigrationImplementation() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);

        SchemaMigration<PayloadV1, PayloadV2> migration = new SchemaMigration<>() {
            @Override
            public PayloadV2 migrate(PayloadV1 source) {
                PayloadV2 target = new PayloadV2();
                target.orderId = source.orderId;
                target.amount = source.amount;
                target.status = "PENDING"; // 기본값
                return target;
            }

            @Override
            public SchemaVersion fromVersion() {
                return v1;
            }

            @Override
            public SchemaVersion toVersion() {
                return v2;
            }
        };

        PayloadV1 source = new PayloadV1();
        source.orderId = "order-123";
        source.amount = 1000;

        // when
        PayloadV2 result = migration.migrate(source);

        // then
        assertNotNull(result);
        assertEquals("order-123", result.orderId);
        assertEquals(1000, result.amount);
        assertEquals("PENDING", result.status);
        assertEquals(v1, migration.fromVersion());
        assertEquals(v2, migration.toVersion());
    }

    @Test
    @DisplayName("isApplicable 테스트 - 적용 가능")
    void testIsApplicable_true() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);

        SchemaMigration<PayloadV1, PayloadV2> migration = createMigration(v1, v2);

        // when
        boolean applicable = migration.isApplicable(v1, v2);

        // then
        assertTrue(applicable);
    }

    @Test
    @DisplayName("isApplicable 테스트 - 적용 불가능 (다른 from 버전)")
    void testIsApplicable_false_differentFrom() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        SchemaVersion v3 = new SchemaVersion("OrderCreated", 3, PayloadV2.class);

        SchemaMigration<PayloadV1, PayloadV2> migration = createMigration(v1, v2);

        // when
        boolean applicable = migration.isApplicable(v3, v2);

        // then
        assertFalse(applicable);
    }

    @Test
    @DisplayName("isApplicable 테스트 - 적용 불가능 (다른 to 버전)")
    void testIsApplicable_false_differentTo() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        SchemaVersion v3 = new SchemaVersion("OrderCreated", 3, PayloadV2.class);

        SchemaMigration<PayloadV1, PayloadV2> migration = createMigration(v1, v2);

        // when
        boolean applicable = migration.isApplicable(v1, v3);

        // then
        assertFalse(applicable);
    }

    @Test
    @DisplayName("복잡한 마이그레이션 테스트")
    void testComplexMigration() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);

        SchemaMigration<PayloadV1, PayloadV2> migration = new SchemaMigration<>() {
            @Override
            public PayloadV2 migrate(PayloadV1 source) {
                PayloadV2 target = new PayloadV2();
                target.orderId = source.orderId;
                target.amount = source.amount * 2; // 비즈니스 로직 적용
                target.status = source.amount > 500 ? "HIGH_VALUE" : "NORMAL";
                return target;
            }

            @Override
            public SchemaVersion fromVersion() {
                return v1;
            }

            @Override
            public SchemaVersion toVersion() {
                return v2;
            }
        };

        PayloadV1 source = new PayloadV1();
        source.orderId = "order-456";
        source.amount = 600;

        // when
        PayloadV2 result = migration.migrate(source);

        // then
        assertEquals("order-456", result.orderId);
        assertEquals(1200, result.amount);
        assertEquals("HIGH_VALUE", result.status);
    }

    @Test
    @DisplayName("null 소스로 마이그레이션 테스트")
    void testMigrationWithNullSource() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);

        SchemaMigration<PayloadV1, PayloadV2> migration = new SchemaMigration<>() {
            @Override
            public PayloadV2 migrate(PayloadV1 source) {
                if (source == null) {
                    return null;
                }
                PayloadV2 target = new PayloadV2();
                target.orderId = source.orderId;
                target.amount = source.amount;
                target.status = "PENDING";
                return target;
            }

            @Override
            public SchemaVersion fromVersion() {
                return v1;
            }

            @Override
            public SchemaVersion toVersion() {
                return v2;
            }
        };

        // when
        PayloadV2 result = migration.migrate(null);

        // then
        assertNull(result);
    }

    private <FROM, TO> SchemaMigration<FROM, TO> createMigration(
            SchemaVersion from,
            SchemaVersion to
    ) {
        return new SchemaMigration<FROM, TO>() {
            @Override
            public TO migrate(FROM source) {
                return null;
            }

            @Override
            public SchemaVersion fromVersion() {
                return from;
            }

            @Override
            public SchemaVersion toVersion() {
                return to;
            }
        };
    }
}
