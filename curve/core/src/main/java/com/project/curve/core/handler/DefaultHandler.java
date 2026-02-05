package com.project.curve.core.handler;

public interface DefaultHandler<T> {
    boolean supports(T strategy);
}
