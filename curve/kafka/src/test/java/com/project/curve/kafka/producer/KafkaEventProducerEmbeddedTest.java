package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventId;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kafka integration test using EmbeddedKafka.
 * Runs Kafka within the JVM without Docker.
 * Faster and lighter than Testcontainers, and more stable in CI/CD environments.
 */
@ExtendWith(SpringExtension.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {"embedded-test-topic", "embedded-test-dlq-topic"}
)
class KafkaEventProducerEmbeddedTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaEventProducer producer;
    private KafkaConsumer<String, String> consumer;
    private KafkaConsumer<String, String> dlqConsumer;

    @BeforeEach
    void setUp() {

        // Kafka Producer Configuration
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", StringSerializer.class);

        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // ObjectMapper Configuration
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Mock dependencies
        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("embedded-test-id-123"));

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
                .topic("embedded-test-topic")
                .dlqTopic("embedded-test-dlq-topic")
                .asyncMode(false)
                .build();

        // Kafka Consumer Configuration
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("embedded-test-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("embedded-test-topic"));

        // DLQ Consumer Configuration
        Map<String, Object> dlqConsumerProps = KafkaTestUtils.consumerProps("embedded-dlq-group", "true", embeddedKafka);
        dlqConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dlqConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dlqConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        dlqConsumer = new KafkaConsumer<>(dlqConsumerProps);
        dlqConsumer.subscribe(Collections.singletonList("embedded-test-dlq-topic"));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
    }

    @Test
    @DisplayName("Verify EmbeddedKafka started successfully")
    void embeddedKafka_shouldBeRunning() {
        // Then
        assertThat(embeddedKafka).isNotNull();
        assertThat(embeddedKafka.getBrokersAsString()).isNotEmpty();
    }

    @Test
    @DisplayName("Should be able to publish events to Kafka")
    void publish_shouldSendEventToKafka() {
        // Given
        TestEventPayload payload = new TestEventPayload("order-1", "embedded-test-data", 100);

        // When & Then
        assertThatNoException().isThrownBy(() ->
                producer.publish(payload, EventSeverity.INFO)
        );
    }

    @Test
    @DisplayName("Should succeed in publishing multiple events consecutively")
    void publish_multipleEvents_shouldSucceed() {
        // Given
        int eventCount = 5;

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
        String testData = "embedded-kafka-message-test-" + System.currentTimeMillis();
        TestEventPayload payload = new TestEventPayload("order-123", testData, 100);

        // When: Publish message
        producer.publish(payload, EventSeverity.INFO);

        // Then: Receive message with Consumer
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        assertThat(records.count()).isGreaterThan(0);

        // Verify message content
        AtomicBoolean foundMessage = new AtomicBoolean(false);

        Iterable<ConsumerRecord<String, String>> recordsList = records.records("embedded-test-topic");
        recordsList.forEach(record -> {
            String value = record.value();
            if (value.contains(testData)) {
                foundMessage.set(true);
            }
        });

        assertThat(foundMessage.get())
                .as("Sent message should be received by Consumer")
                .isTrue();
    }

    @Test
    @DisplayName("Published message should include event metadata")
    void publish_shouldIncludeEventMetadata() throws Exception {
        // Given
        String testData = "metadata-test-" + System.currentTimeMillis();
        TestEventPayload payload = new TestEventPayload("order-456", testData, 200);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // When: Publish message
        producer.publish(payload, EventSeverity.INFO);

        // Then: Receive and verify message with Consumer
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();

        Iterable<ConsumerRecord<String, String>> recordIterable = records.records("embedded-test-topic");

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
        assertThat(dlqConsumer.subscription()).contains("embedded-test-dlq-topic");
    }

    @Test
    @DisplayName("Should be able to publish events in async mode")
    void publish_asyncMode_shouldSendEventSuccessfully() throws InterruptedException {
        // Given: Async mode producer
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", StringSerializer.class);

        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);

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
                .topic("embedded-test-topic")
                .asyncMode(true)
                .asyncTimeoutMs(5000L)
                .build();

        String uniqueData = "async-test-data-" + System.currentTimeMillis();
        TestEventPayload payload = new TestEventPayload("async-order-789", uniqueData, 100);

        // When: Publish async
        asyncProducer.publish(payload, EventSeverity.INFO);

        // Wait for async completion
        Thread.sleep(2000);

        // Then: Message should be in Kafka
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).isNotEmpty();

        boolean foundAsyncMessage = false;
        for (ConsumerRecord<String, String> record : records.records("embedded-test-topic")) {
            if (record.value().contains(uniqueData)) {
                foundAsyncMessage = true;
                break;
            }
        }
        assertThat(foundAsyncMessage).isTrue();
    }
}
