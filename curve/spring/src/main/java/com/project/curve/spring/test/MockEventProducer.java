package com.project.curve.spring.test;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EventProducer implementation for testing purposes.
 * <p>
 * Stores events in memory instead of publishing to Kafka for verification.
 *
 * <h3>Usage Example</h3>
 * <pre>
 * @TestConfiguration
 * public class TestConfig {
 *     @Bean
 *     @Primary
 *     public EventProducer eventProducer() {
 *         return new MockEventProducer();
 *     }
 * }
 *
 * @Autowired
 * private MockEventProducer mockProducer;
 *
 * @Test
 * void test() {
 *     // ...
 *     assertThat(mockProducer.getEvents()).hasSize(1);
 * }
 * </pre>
 */
public class MockEventProducer implements EventProducer {

    private final List<Object> payloads = new CopyOnWriteArrayList<>();
    private final List<EventSeverity> severities = new CopyOnWriteArrayList<>();

    @Override
    public <T extends DomainEventPayload> void publish(T payload) {
        publish(payload, EventSeverity.INFO);
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload, EventSeverity severity) {
        payloads.add(payload);
        severities.add(severity);
    }

    public List<Object> getPayloads() {
        return Collections.unmodifiableList(payloads);
    }

    public List<EventSeverity> getSeverities() {
        return Collections.unmodifiableList(severities);
    }

    public void clear() {
        payloads.clear();
        severities.clear();
    }
}
