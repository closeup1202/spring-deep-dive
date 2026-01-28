package com.exam.springevents.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(Object event) {
        log.info(">>> [Publisher] Publishing event: {}", event);
        applicationEventPublisher.publishEvent(event);
    }
}