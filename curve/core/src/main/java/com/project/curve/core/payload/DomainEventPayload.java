package com.project.curve.core.payload;

import com.project.curve.core.type.EventType;

// 모든 비즈니스 이벤트(DTO)가 구현해야 하는 마커 인터페이스
public interface DomainEventPayload {
    EventType getEventType();   // 페이로드가 스스로 자신의 타입을 알리도록 강제
}