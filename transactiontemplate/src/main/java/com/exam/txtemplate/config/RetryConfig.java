package com.exam.txtemplate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

@Configuration
public class RetryConfig {

    /**
     * 낙관적 락 충돌 시 재시도 전략.
     *
     * - 최대 3회 재시도
     * - ExponentialBackOff: 100ms → 200ms → 400ms
     *   동시 요청이 많을 때 thundering herd 방지.
     *   FixedBackOff는 모든 스레드가 동시에 재시도해서 충돌을 반복시킨다.
     */
    @Bean
    public RetryTemplate optimisticLockRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        template.setRetryPolicy(new SimpleRetryPolicy(
            3,
            Map.of(ObjectOptimisticLockingFailureException.class, true)
        ));

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(100);   // 첫 재시도 대기: 100ms
        backOff.setMultiplier(2.0);        // 재시도마다 2배씩 증가
        backOff.setMaxInterval(1_000);     // 최대 1초 이상은 대기하지 않음
        template.setBackOffPolicy(backOff);

        return template;
    }
}
