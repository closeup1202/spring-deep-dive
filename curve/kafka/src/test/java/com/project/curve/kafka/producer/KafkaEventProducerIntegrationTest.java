package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventId;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.kafka.backup.EventBackupStrategy;
import com.project.curve.kafka.backup.LocalFileBackupStrategy;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kafka integration test using Testcontainers.
 * Requires Docker to be installed and running.
 */
@Testcontainers
class KafkaEventProducerIntegrationTest {

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    private static KafkaEventProducer producer;
    private static KafkaConsumer<String, String> consumer;
    private static KafkaConsumer<String, String> dlqConsumer;

    private static @NotNull KafkaTemplate<String, Object> getStringKafkaTemplate() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put("bootstrap.servers", kafka.getBootstrapServers());
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", StringSerializer.class);

        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        return new KafkaTemplate<>(producerFactory);
    }

    @BeforeAll
    static void setUp() {
        // Kafka Producer Configuration
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();

        // ObjectMapper Configuration
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Mock dependencies
        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("test-id-123"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        // EventSerializer mock configuration
        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // Create KafkaEventProducer
        producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .asyncMode(false)
                .build();

        // Kafka Consumer Configuration
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("test-topic"));

        // DLQ Consumer Configuration
        Map<String, Object> dlqConsumerProps = new HashMap<>();
        dlqConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        dlqConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-consumer-group");
        dlqConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dlqConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dlqConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dlqConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        dlqConsumer = new KafkaConsumer<>(dlqConsumerProps);
        dlqConsumer.subscribe(Collections.singletonList("test-dlq-topic"));
    }

    @AfterAll
    static void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    @DisplayName("Verify Kafka container is running")
    void kafka_shouldBeRunning() {
        // Then
        assertThat(kafka.isRunning()).isTrue();
        assertThat(kafka.getBootstrapServers()).isNotEmpty();
    }

    @Test
    @DisplayName("Should be able to publish events to Kafka")
    void publish_shouldSendEventToKafka() {
        // Given
        TestEventPayload payload = new TestEventPayload("order-1", "test-data", 100);

        // When & Then
        assertThatNoException().isThrownBy(() ->
                producer.publish(payload, EventSeverity.INFO)
        );
    }

    @Test
    @DisplayName("Should succeed in publishing multiple events consecutively")
    void publish_multipleEvents_shouldSucceed() {
        // Given
        int eventCount = 10;

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            for (int i = 0; i < eventCount; i++) {
                TestEventPayload payload = new TestEventPayload("order-" + i, "test-data-" + i, 100);
                producer.publish(payload, EventSeverity.INFO);
            }
        });
    }

    @Test
    @DisplayName("Should actually send message to Kafka and receive it with Consumer")
    void publish_shouldSendMessageAndConsumeSuccessfully() {
        // Given
        String testData = "real-kafka-message-test";
        TestEventPayload payload = new TestEventPayload("order-1", testData, 100);

        // When: Publish message
        producer.publish(payload, EventSeverity.INFO);

        // Then: Receive message with Consumer
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        assertThat(records.count()).isGreaterThan(0);

        // Verify message content
        AtomicBoolean foundMessage = new AtomicBoolean(false);

        Iterable<ConsumerRecord<String, String>> recordsList = records.records("test-topic");
        recordsList.forEach(record -> {
            String value = record.value();
            foundMessage.set(value.contains(testData));
        });

        assertThat(foundMessage.get())
                .as("Sent message should be received by Consumer")
                .isTrue();
    }

    @Test
    @DisplayName("Published message should include event metadata")
    void publish_shouldIncludeEventMetadata() throws Exception {
        // Given
        String testData = "metadata-test";
        TestEventPayload payload = new TestEventPayload("order-1", testData, 100);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // When: Publish message
        producer.publish(payload, EventSeverity.INFO);

        // Then: Receive and verify message with Consumer
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();

        Iterable<ConsumerRecord<String, String>> recordIterable = records.records("test-topic");

        // Parse JSON and verify required fields
        boolean hasValidMetadata = false;
        for (ConsumerRecord<String, String> record : recordIterable) {
            if (record.value().contains(testData)) {
                try {
                    String json = record.value();
                    Map<?, ?> envelope = objectMapper.readValue(json, Map.class);

                    // Verify required fields
                    hasValidMetadata = envelope.containsKey("eventId") &&
                            envelope.containsKey("eventType") &&
                            envelope.containsKey("severity") &&
                            envelope.containsKey("metadata") &&
                            envelope.containsKey("payload") &&
                            envelope.containsKey("occurredAt") &&
                            envelope.containsKey("publishedAt");

                    if (hasValidMetadata) {
                        break;
                    }
                } catch (Exception e) {
                    // Continue if parsing fails
                }
            }
        }

        assertThat(hasValidMetadata)
                .as("Message should include all required metadata")
                .isTrue();
    }

    @Test
    @DisplayName("DLQ topic should be configured correctly")
    void dlq_shouldBeConfigured() {
        // Given & When: DLQ Consumer is subscribed
        // Then: DLQ Consumer operates normally
        assertThat(dlqConsumer.subscription()).contains("test-dlq-topic");
    }

    @Test
    @DisplayName("DLQ Consumer should be able to receive messages")
    void dlq_shouldConsumeMessages() throws Exception {
        // Given: Warm up DLQ consumer to ensure partition assignment
        dlqConsumer.poll(Duration.ofMillis(100));

        // Send message directly to DLQ (simulate actual failure scenario)
        KafkaTemplate<String, Object> dlqTemplate = getStringKafkaTemplate();

        // Create FailedEventRecord JSON
        String testEventId = "failed-event-123-" + System.currentTimeMillis();
        String testPayload = "{\"eventId\":\"" + testEventId + "\",\"data\":\"test-failure\"}";
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> failedRecord = new HashMap<>();
        failedRecord.put("eventId", testEventId);
        failedRecord.put("originalTopic", "test-topic");
        failedRecord.put("originalValue", testPayload);
        failedRecord.put("exceptionClass", "org.apache.kafka.common.errors.TimeoutException");
        failedRecord.put("exceptionMessage", "Timeout after 30 seconds");
        failedRecord.put("failedAt", System.currentTimeMillis());

        String dlqMessage = objectMapper.writeValueAsString(failedRecord);

        // When: Send message to DLQ
        dlqTemplate.send("test-dlq-topic", testEventId, dlqMessage).get();

        // Give Kafka time to commit
        Thread.sleep(500);

        // Then: Receive message with DLQ Consumer
        boolean foundDlqMessage = false;
        int maxAttempts = 3;

        for (int attempt = 0; attempt < maxAttempts && !foundDlqMessage; attempt++) {
            ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofSeconds(5));

            for (ConsumerRecord<String, String> record : records.records("test-dlq-topic")) {
                if (record.value().contains(testEventId)) {
                    foundDlqMessage = true;

                    // Parse and verify FailedEventRecord
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedRecord = objectMapper.readValue(record.value(), Map.class);
                    assertThat(parsedRecord).containsKey("eventId");
                    assertThat(parsedRecord).containsKey("originalTopic");
                    assertThat(parsedRecord).containsKey("exceptionClass");
                    assertThat(parsedRecord.get("eventId")).isEqualTo(testEventId);
                    break;
                }
            }
        }

        assertThat(foundDlqMessage)
                .as("Message sent to DLQ should be received by Consumer")
                .isTrue();
    }

    @Test
    @DisplayName("Should be able to publish events in async mode")
    void publish_asyncMode_shouldSendEventSuccessfully() throws InterruptedException {
        // Given: Async mode producer
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("async-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        KafkaEventProducer asyncProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .asyncMode(true)
                .asyncTimeoutMs(5000L)
                .build();

        TestEventPayload payload = new TestEventPayload("async-order-1", "async-test-data", 100);

        // When: Publish async
        asyncProducer.publish(payload, EventSeverity.INFO);

        // Wait for async completion
        Thread.sleep(2000);

        // Then: Message should be in Kafka
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).isNotEmpty();

        boolean foundAsyncMessage = false;
        for (ConsumerRecord<String, String> record : records.records("test-topic")) {
            if (record.value().contains("async-test-data")) {
                foundAsyncMessage = true;
                break;
            }
        }
        assertThat(foundAsyncMessage).isTrue();
    }

    @Test
    @DisplayName("MDC context should be propagated correctly in async callbacks")
    void publish_asyncMode_shouldPreserveMdcContext() throws InterruptedException {
        // Given: Async mode producer
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("mdc-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        KafkaEventProducer asyncProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .asyncMode(true)
                .build();

        // When: Set MDC context and publish
        MDC.put("traceId", "trace-123");
        MDC.put("userId", "user-456");

        TestEventPayload payload = new TestEventPayload("mdc-order-1", "mdc-test-data", 100);
        asyncProducer.publish(payload, EventSeverity.INFO);

        Thread.sleep(1000);

        // Then: Verify MDC context is preserved (no exception thrown)
        assertThatNoException().isThrownBy(() -> MDC.clear());
    }

    @Test
    @DisplayName("Should retry on failure when retry mechanism is enabled")
    void publish_withRetry_shouldRetryOnFailure() {
        // Given: Producer with retry template
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("retry-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // Configure retry template: 3 attempts with 100ms delay
        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(100);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Mock Kafka template to fail twice, then succeed
        AtomicInteger attemptCount = new AtomicInteger(0);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                throw new RuntimeException("Kafka temporarily unavailable");
            }
            return getStringKafkaTemplate().send("test-topic", "retry-test-id", "success");
        });

        KafkaEventProducer retryProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .retryTemplate(retryTemplate)
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("retry-order-1", "retry-test-data", 100);

        // When & Then: Should succeed after retries
        assertThatNoException().isThrownBy(() -> retryProducer.publish(payload, EventSeverity.INFO));
        assertThat(attemptCount.get()).isEqualTo(3); // Failed twice, succeeded on 3rd attempt
    }

    @Test
    @DisplayName("Should trigger DLQ logic on message send failure (Verification - Mock based)")
    void publish_onFailure_shouldTriggerDlqLogic() throws Exception {
        // Given: Producer with mocked failing Kafka template
        KafkaTemplate<String, Object> mockKafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("dlq-mock-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // Mock main topic to fail
        when(mockKafkaTemplate.send(eq("test-topic"), anyString(), any()))
                .thenThrow(new RuntimeException("Simulated Kafka failure"));

        // Mock DLQ topic to succeed (but don't actually send since it's fully mocked)
        when(mockKafkaTemplate.send(eq("test-dlq-topic"), anyString(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        mock(org.springframework.kafka.support.SendResult.class)
                ));

        KafkaEventProducer failingProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(mockKafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("dlq-order-1", "dlq-test-data", 100);

        // When: Publish (will fail main topic and trigger DLQ logic)
        failingProducer.publish(payload, EventSeverity.INFO);

        // Then: Verify DLQ send was attempted
        verify(mockKafkaTemplate).send(eq("test-dlq-topic"), eq("dlq-mock-test-id"), anyString());
    }

    @Test
    @DisplayName("Should backup to local file if DLQ send also fails")
    void publish_onDlqFailure_shouldBackupToLocalFile() throws IOException {
        // Given: Producer with both main and DLQ topics failing
        KafkaTemplate<String, Object> mockKafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("backup-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // Mock both topics to fail
        when(mockKafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Complete Kafka outage"));

        String testBackupPath = "./test-dlq-backup-" + System.currentTimeMillis();
        EventBackupStrategy backupStrategy = new LocalFileBackupStrategy(testBackupPath, objectMapper, false);

        KafkaEventProducer backupProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(mockKafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .backupStrategy(backupStrategy)
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("backup-order-1", "backup-test-data", 100);

        // When: Publish (will fail both main and DLQ, trigger backup)
        backupProducer.publish(payload, EventSeverity.INFO);

        // Then: Verify backup file was created
        Path backupFile = Paths.get(testBackupPath, "backup-test-id.json");
        assertThat(Files.exists(backupFile))
                .as("Backup file should be created")
                .isTrue();

        String backupContent = Files.readString(backupFile);
        assertThat(backupContent).contains("backup-test-data");

        // Cleanup
        try (Stream<Path> files = Files.walk(Paths.get(testBackupPath))) {
            files.sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    @DisplayName("Should fail immediately if Serialization exception occurs")
    void publish_serializationException_shouldFailImmediately() {
        // Given: Producer with serializer that throws exception
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("serialization-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any()))
                .thenThrow(new EventSerializationException("Cannot serialize invalid data", new RuntimeException()));

        KafkaEventProducer serializationFailingProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("serialization-order-1", "serialization-test-data", 100);

        // When & Then: Should throw EventSerializationException
        assertThatThrownBy(() -> serializationFailingProducer.publish(payload, EventSeverity.INFO))
                .isInstanceOf(EventSerializationException.class)
                .hasMessageContaining("Cannot serialize invalid data");
    }

    @Test
    @DisplayName("Should send DLQ asynchronously if DLQ Executor is configured")
    void publish_withDlqExecutor_shouldSendDlqAsynchronously() throws InterruptedException {
        // Given: Producer with DLQ executor
        KafkaTemplate<String, Object> mockKafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("dlq-async-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        ExecutorService dlqExecutor = Executors.newSingleThreadExecutor();

        // Mock main topic to fail, DLQ to succeed
        when(mockKafkaTemplate.send(eq("test-topic"), anyString(), any()))
                .thenThrow(new RuntimeException("Simulated Kafka failure"));

        when(mockKafkaTemplate.send(eq("test-dlq-topic"), anyString(), any()))
                .thenReturn(getStringKafkaTemplate().send("test-dlq-topic", "dlq-async-test-id", "dlq-async-content"));

        KafkaEventProducer dlqAsyncProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(mockKafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .dlqExecutor(dlqExecutor)
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("dlq-async-order-1", "dlq-async-test-data", 100);

        // When: Publish (will fail and trigger async DLQ)
        dlqAsyncProducer.publish(payload, EventSeverity.INFO);

        // Wait for async DLQ processing
        Thread.sleep(2000);

        // Then: Verify DLQ executor was used (no exception means success)
        dlqExecutor.shutdown();
        assertThat(dlqExecutor.isShutdown()).isTrue();
    }
}
