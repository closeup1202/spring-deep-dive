package com.exam.jvmheap.config;

import com.exam.jvmheap.memory.LeakySessionStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LabConfig {

    /**
     * 싱글턴 빈이 컬렉션을 들고 있으면 그 컬렉션은 GC Root 에서 도달 가능하다.
     * 애플리케이션이 살아있는 한 절대 수거되지 않는다.
     */
    @Bean
    public LeakySessionStore leakySessionStore() {
        return new LeakySessionStore();
    }

    /**
     * 커스텀 지표 등록.
     *
     * <p>jvm.memory.used 같은 표준 지표는 actuator 가 알아서 노출한다.
     * 여기서 추가하는 것은 "무엇이 힙을 먹고 있는가" 를 도메인 언어로 설명하는 지표다.
     * 장애 대응에서 실제로 쓸모 있는 것은 후자다.
     */
    @Bean
    public InitializingBean labMetrics(MeterRegistry registry, LeakySessionStore store) {
        return () -> {
            Gauge.builder("lab.sessions.count", store, LeakySessionStore::size)
                    .description("누수 저장소에 남아있는 세션 수")
                    .register(registry);
            Gauge.builder("lab.sessions.bytes", store, LeakySessionStore::approximateBytes)
                    .description("누수 저장소가 붙잡고 있는 대략적인 바이트")
                    .baseUnit("bytes")
                    .register(registry);
        };
    }
}
