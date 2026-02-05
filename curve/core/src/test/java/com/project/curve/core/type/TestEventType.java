package com.project.curve.core.type;

public record TestEventType(String name) implements EventType {
    @Override
    public String getValue() {
        return name;
    }
}