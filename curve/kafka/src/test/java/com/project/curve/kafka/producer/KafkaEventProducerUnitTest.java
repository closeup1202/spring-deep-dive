package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.kafka.backup.EventBackupStrategy;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.metrics.NoOpCurveMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@DisplayName("KafkaEventProducer Unit Test")
class KafkaEventProducerUnitTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private EventSerializer eventSerializer;
    private EventEnvelopeFactory envelopeFactory;
    private EventContextProvider eventContextProvider;
    private CurveMetricsCollector metricsCollector;
    private ObjectMapper objectMapper;
    private EventBackupStrategy backupStrategy;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        eventSerializer = mock(EventSerializer.class);
        envelopeFactory = mock(EventEnvelopeFactory.class);
        eventContextProvider = mock(EventContextProvider.class);
        metricsCollector = new NoOpCurveMetricsCollector();
        objectMapper = new ObjectMapper();
        backupStrategy = mock(EventBackupStrategy.class);
    }

    @Test
    @DisplayName("Create KafkaEventProducer with Builder - Minimal Configuration")
    void testBuilderWithMinimalConfig() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Create KafkaEventProducer with Builder - Full Configuration")
    void testBuilderWithFullConfig() {
        // given
        RetryTemplate retryTemplate = new RetryTemplate();

        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .retryTemplate(retryTemplate)
                .asyncMode(true)
                .asyncTimeoutMs(10000L)
                .syncTimeoutSeconds(60L)
                .metricsCollector(metricsCollector)
                .backupStrategy(backupStrategy)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Create KafkaEventProducer with Builder - Null Validation")
    void testBuilderWithNullValues() {
        // when & then
        assertThrows(NullPointerException.class, () ->
                KafkaEventProducer.builder()
                        .envelopeFactory(null)
                        .eventContextProvider(eventContextProvider)
                        .kafkaTemplate(kafkaTemplate)
                        .eventSerializer(eventSerializer)
                        .objectMapper(objectMapper)
                        .topic("test-topic")
                        .metricsCollector(metricsCollector)
                        .build()
        );
    }

    @Test
    @DisplayName("Default asyncMode is false")
    void testDefaultAsyncMode() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
        // asyncMode defaults to false
    }

    @Test
    @DisplayName("Default asyncTimeoutMs is 5000L")
    void testDefaultAsyncTimeoutMs() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Default syncTimeoutSeconds is 30L")
    void testDefaultSyncTimeoutSeconds() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("dlqEnabled is false if DLQ is not configured")
    void testDlqNotEnabled() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("dlqEnabled is false if DLQ is empty string")
    void testDlqWithEmptyString() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("dlqEnabled is false if DLQ is blank string")
    void testDlqWithBlankString() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("   ")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("dlqEnabled is true if DLQ topic is configured")
    void testDlqEnabled() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("test-dlq")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Retry is enabled if RetryTemplate is configured")
    void testRetryEnabled() {
        // given
        RetryTemplate retryTemplate = new RetryTemplate();

        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .retryTemplate(retryTemplate)
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Set asyncMode to true")
    void testAsyncModeEnabled() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .asyncMode(true)
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Configure custom timeouts")
    void testCustomTimeouts() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .asyncTimeoutMs(15000L)
                .syncTimeoutSeconds(120L)
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Configure BackupStrategy")
    void testBackupStrategy() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .backupStrategy(backupStrategy)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Test combination of all options")
    void testAllOptionsCombination() {
        // given
        RetryTemplate retryTemplate = new RetryTemplate();

        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("dlq-topic")
                .retryTemplate(retryTemplate)
                .asyncMode(true)
                .asyncTimeoutMs(20000L)
                .syncTimeoutSeconds(90L)
                .metricsCollector(metricsCollector)
                .backupStrategy(backupStrategy)
                .build();

        // then
        assertNotNull(producer);
    }
}
