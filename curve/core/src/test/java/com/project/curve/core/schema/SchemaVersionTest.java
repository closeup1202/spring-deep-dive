package com.project.curve.core.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaVersion 테스트")
class SchemaVersionTest {

    static class TestPayloadV1 {}
    static class TestPayloadV2 {}

    @Test
    @DisplayName("정상적인 SchemaVersion 생성")
    void createValidSchemaVersion() {
        // given
        String name = "OrderCreated";
        int version = 1;
        Class<?> payloadClass = TestPayloadV1.class;

        // when
        SchemaVersion schemaVersion = new SchemaVersion(name, version, payloadClass);

        // then
        assertNotNull(schemaVersion);
        assertEquals(name, schemaVersion.name());
        assertEquals(version, schemaVersion.version());
        assertEquals(payloadClass, schemaVersion.payloadClass());
    }

    @Test
    @DisplayName("name이 null이면 예외 발생")
    void createSchemaVersionWithNullName_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaVersion(null, 1, TestPayloadV1.class)
        );
        assertEquals("Schema name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("name이 빈 문자열이면 예외 발생")
    void createSchemaVersionWithEmptyName_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaVersion("", 1, TestPayloadV1.class)
        );
        assertEquals("Schema name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("name이 공백만 있으면 예외 발생")
    void createSchemaVersionWithBlankName_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaVersion("   ", 1, TestPayloadV1.class)
        );
        assertEquals("Schema name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("version이 0이면 예외 발생")
    void createSchemaVersionWithZeroVersion_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaVersion("OrderCreated", 0, TestPayloadV1.class)
        );
        assertTrue(exception.getMessage().contains("must be >= 1"));
    }

    @Test
    @DisplayName("version이 음수면 예외 발생")
    void createSchemaVersionWithNegativeVersion_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaVersion("OrderCreated", -1, TestPayloadV1.class)
        );
        assertTrue(exception.getMessage().contains("must be >= 1"));
    }

    @Test
    @DisplayName("payloadClass가 null이면 예외 발생")
    void createSchemaVersionWithNullPayloadClass_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaVersion("OrderCreated", 1, null)
        );
        assertEquals("Payload class must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("getKey 메서드 테스트")
    void testGetKey() {
        // given
        SchemaVersion schemaVersion = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);

        // when
        String key = schemaVersion.getKey();

        // then
        assertEquals("OrderCreated:v1", key);
    }

    @Test
    @DisplayName("getKey 메서드 테스트 - 버전 2")
    void testGetKey_version2() {
        // given
        SchemaVersion schemaVersion = new SchemaVersion("OrderCreated", 2, TestPayloadV2.class);

        // when
        String key = schemaVersion.getKey();

        // then
        assertEquals("OrderCreated:v2", key);
    }

    @Test
    @DisplayName("compareVersion 테스트 - 같은 버전")
    void testCompareVersion_equal() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);

        // when
        int result = v1.compareVersion(v2);

        // then
        assertEquals(0, result);
    }

    @Test
    @DisplayName("compareVersion 테스트 - 더 큰 버전")
    void testCompareVersion_greater() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 2, TestPayloadV2.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);

        // when
        int result = v1.compareVersion(v2);

        // then
        assertTrue(result > 0);
    }

    @Test
    @DisplayName("compareVersion 테스트 - 더 작은 버전")
    void testCompareVersion_less() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, TestPayloadV2.class);

        // when
        int result = v1.compareVersion(v2);

        // then
        assertTrue(result < 0);
    }

    @Test
    @DisplayName("compareVersion 테스트 - 다른 스키마 이름이면 예외 발생")
    void testCompareVersion_differentSchemaName_shouldThrowException() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("UserRegistered", 1, TestPayloadV1.class);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> v1.compareVersion(v2)
        );
        assertTrue(exception.getMessage().contains("Cannot compare versions of different schemas"));
    }

    @Test
    @DisplayName("isNewerThan 테스트 - 더 최신 버전")
    void testIsNewerThan_true() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 2, TestPayloadV2.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);

        // when
        boolean isNewer = v1.isNewerThan(v2);

        // then
        assertTrue(isNewer);
    }

    @Test
    @DisplayName("isNewerThan 테스트 - 같은 버전")
    void testIsNewerThan_false_equal() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);

        // when
        boolean isNewer = v1.isNewerThan(v2);

        // then
        assertFalse(isNewer);
    }

    @Test
    @DisplayName("isNewerThan 테스트 - 더 오래된 버전")
    void testIsNewerThan_false_older() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, TestPayloadV2.class);

        // when
        boolean isNewer = v1.isNewerThan(v2);

        // then
        assertFalse(isNewer);
    }

    @Test
    @DisplayName("isCompatibleWith 테스트 - 같은 스키마 이름")
    void testIsCompatibleWith_true() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, TestPayloadV2.class);

        // when
        boolean compatible = v1.isCompatibleWith(v2);

        // then
        assertTrue(compatible);
    }

    @Test
    @DisplayName("isCompatibleWith 테스트 - 다른 스키마 이름")
    void testIsCompatibleWith_false() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("UserRegistered", 1, TestPayloadV1.class);

        // when
        boolean compatible = v1.isCompatibleWith(v2);

        // then
        assertFalse(compatible);
    }

    @Test
    @DisplayName("equals 및 hashCode 테스트")
    void testEqualsAndHashCode() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);
        SchemaVersion v3 = new SchemaVersion("OrderCreated", 2, TestPayloadV2.class);

        // then
        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    @DisplayName("toString 테스트")
    void testToString() {
        // given
        SchemaVersion schemaVersion = new SchemaVersion("OrderCreated", 1, TestPayloadV1.class);

        // when
        String toString = schemaVersion.toString();

        // then
        assertNotNull(toString);
        assertTrue(toString.contains("OrderCreated"));
        assertTrue(toString.contains("1"));
    }
}
