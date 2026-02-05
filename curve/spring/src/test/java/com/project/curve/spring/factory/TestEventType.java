package com.project.curve.spring.factory;

import com.project.curve.core.type.EventType;

public class TestEventType implements EventType {
    @Override
    public String getValue() {
        return "TEST-EVENT-TYPE";
    }
}
