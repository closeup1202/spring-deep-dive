package com.example.actuator.endpoint;

import lombok.Data;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 커스텀 Actuator 엔드포인트
 *
 * @Endpoint 어노테이션을 사용하면 /actuator/custom 형태의
 * 새로운 엔드포인트를 추가할 수 있습니다.
 *
 * 사용 사례:
 * - 애플리케이션 특화된 운영 정보 노출
 * - 캐시 통계, 비즈니스 메트릭 조회
 * - 운영 중 설정 변경 (Feature Flag 등)
 */
@Component
@Endpoint(id = "custom")
public class CustomEndpoint {

    private final Map<String, Object> customData = new HashMap<>();
    private int requestCount = 0;

    /**
     * GET /actuator/custom
     * 읽기 전용 작업
     */
    @ReadOperation
    public CustomInfo getCustomInfo() {
        requestCount++;

        CustomInfo info = new CustomInfo();
        info.setApplicationName("Actuator Deep Dive");
        info.setVersion("1.0.0");
        info.setEnvironment("development");
        info.setUptime(getUptime());
        info.setRequestCount(requestCount);
        info.setCustomData(customData);
        info.setTimestamp(LocalDateTime.now());

        return info;
    }

    /**
     * POST /actuator/custom
     * 쓰기 작업 (설정 변경 등)
     */
    @WriteOperation
    public Map<String, String> updateCustomData(String key, String value) {
        customData.put(key, value);
        return Map.of(
                "message", "Custom data updated",
                "key", key,
                "value", value
        );
    }

    private String getUptime() {
        long uptimeMs = System.currentTimeMillis() -
                        java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
    }

    @Data
    public static class CustomInfo {
        private String applicationName;
        private String version;
        private String environment;
        private String uptime;
        private int requestCount;
        private Map<String, Object> customData;
        private LocalDateTime timestamp;
    }
}
