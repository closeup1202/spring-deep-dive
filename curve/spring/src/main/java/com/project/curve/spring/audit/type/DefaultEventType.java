package com.project.curve.spring.audit.type;

import com.project.curve.core.type.EventType;

public record DefaultEventType(String name) implements EventType {
    @Override
    public String getValue() {
        return name;
    }
}
