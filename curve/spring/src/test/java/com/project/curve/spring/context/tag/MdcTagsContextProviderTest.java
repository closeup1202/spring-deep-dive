package com.project.curve.spring.context.tag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MdcTagsContextProvider Test")
class MdcTagsContextProviderTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("Default Constructor Test")
    class DefaultConstructorTest {

        @Test
        @DisplayName("Default constructor should extract region and tenant")
        void getTags_withDefaultKeys_shouldExtractRegionAndTenant() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MDC.put("tenant", "company-001");
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("region", "ap-northeast-2")
                    .containsEntry("tenant", "company-001")
                    .hasSize(2);
        }

        @Test
        @DisplayName("Should return empty map if MDC has no values")
        void getTags_withNoMdcValues_shouldReturnEmptyMap() {
            // Given
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags).isEmpty();
        }

        @Test
        @DisplayName("Should return only set keys if partial values are set")
        void getTags_withPartialValues_shouldReturnOnlySetKeys() {
            // Given
            MDC.put("region", "us-east-1");
            // tenant is not set
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("region", "us-east-1")
                    .doesNotContainKey("tenant")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("Custom Keys Test")
    class CustomKeysTest {

        @Test
        @DisplayName("Should use custom keys with withKeys")
        void withKeys_shouldUseCustomKeys() {
            // Given
            MDC.put("customKey1", "value1");
            MDC.put("customKey2", "value2");
            MdcTagsContextProvider provider = MdcTagsContextProvider.withKeys("customKey1", "customKey2");

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("customKey1", "value1")
                    .containsEntry("customKey2", "value2")
                    .hasSize(2);
        }

        @Test
        @DisplayName("Should ignore default keys if custom keys are set")
        void withKeys_shouldIgnoreDefaultKeys() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MDC.put("customKey", "customValue");
            MdcTagsContextProvider provider = MdcTagsContextProvider.withKeys("customKey");

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("customKey", "customValue")
                    .doesNotContainKey("region")
                    .hasSize(1);
        }

        @Test
        @DisplayName("Should return empty map if created with empty key array")
        void withKeys_withEmptyArray_shouldReturnEmptyMap() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MdcTagsContextProvider provider = MdcTagsContextProvider.withKeys();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags).isEmpty();
        }
    }

    @Nested
    @DisplayName("Immutability Test")
    class ImmutabilityTest {

        @Test
        @DisplayName("Returned map should be immutable")
        void getTags_shouldReturnImmutableMap() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThatThrownBy(() -> tags.put("newKey", "newValue"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("tagKeys should be immutable")
        void tagKeys_shouldBeImmutable() {
            // Given
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When & Then
            assertThatThrownBy(() -> provider.tagKeys().add("newKey"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Null Safety Test")
    class NullSafetyTest {

        @Test
        @DisplayName("Should not throw NPE even if MDC value is null")
        void getTags_withNullMdcValue_shouldNotThrowNpe() {
            // Given
            // MDC.get() returns null for non-existent keys
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When & Then
            assertThatCode(() -> provider.getTags()).doesNotThrowAnyException();
        }
    }
}
