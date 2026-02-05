package com.project.curve.spring.context;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

/**
 * TaskDecorator that propagates parent thread context to child threads during asynchronous task execution.
 * <p>
 * - RequestContextHolder: Propagates HTTP request-related information (Request, Session, etc.)
 * - MDC (Mapped Diagnostic Context): Propagates logging trace IDs, etc.
 * <p>
 * Used with @EnableAsync to prevent context loss when calling @Async methods.
 */
public class ContextAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            try {
                RequestContextHolder.setRequestAttributes(requestAttributes);
                MDC.setContextMap(mdcContext);
                runnable.run();
            } finally {
                MDC.clear();
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }
}
