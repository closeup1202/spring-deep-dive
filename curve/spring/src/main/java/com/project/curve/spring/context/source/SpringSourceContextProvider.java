package com.project.curve.spring.context.source;

import com.project.curve.core.context.CorrelationContextProvider;
import com.project.curve.core.context.SourceContextProvider;
import com.project.curve.core.envelope.EventSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring 기반 EventSource 제공자.
 * <p>
 * CorrelationContextProvider를 사용하여 Event Chain Tracking을 지원합니다.
 */
@Slf4j
public class SpringSourceContextProvider implements SourceContextProvider {

    private final String service;
    private final String environment;
    private final String instanceId;
    private final String host;
    private final String version;
    private final CorrelationContextProvider correlationContextProvider;

    public SpringSourceContextProvider(
            String service,
            Environment env,
            String version,
            CorrelationContextProvider correlationContextProvider
    ) {
        this.service = service;
        this.environment = determineEnvironment(env);
        this.instanceId = resolveInstanceId();
        this.host = resolveHost();
        this.version = version;
        this.correlationContextProvider = correlationContextProvider;
    }

    @Override
    public EventSource getSource() {
        // Correlation ID 조회 (MDC 또는 다른 컨텍스트에서)
        String correlationId = correlationContextProvider != null
                ? correlationContextProvider.getCorrelationId()
                : null;

        String causationId = correlationContextProvider != null
                ? correlationContextProvider.getCausationId()
                : null;

        String rootEventId = correlationContextProvider != null
                ? correlationContextProvider.getRootEventId()
                : null;

        return new EventSource(
                service,
                environment,
                instanceId,
                host,
                version,
                correlationId,
                causationId,
                rootEventId
        );
    }

    private String determineEnvironment(Environment env) {
        String[] activeProfiles = env.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return String.join(",", activeProfiles);
        }
        return env.getProperty("spring.profiles.default", "default");
    }

    private String resolveInstanceId() {
        return Optional.ofNullable(System.getenv("HOSTNAME"))
                .filter(h -> !h.isBlank())
                .orElseGet(() -> {
                    log.warn("HOSTNAME not set, using UUID for instance ID");
                    return UUID.randomUUID().toString();
                });
    }

    private String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Failed to resolve hostname, using 'unknown': {}", e.getMessage());
            return "unknown";
        }
    }
}
