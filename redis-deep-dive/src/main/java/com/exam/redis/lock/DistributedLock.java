package com.exam.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    
    // 락의 키 (SpEL 표현식 지원 예정)
    String key();

    // 락 획득 대기 시간
    long waitTime() default 5L;

    // 락 점유 시간 (이 시간이 지나면 자동 해제)
    long leaseTime() default 3L;

    // 시간 단위
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
