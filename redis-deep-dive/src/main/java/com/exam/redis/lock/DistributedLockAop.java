package com.exam.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAop {

    private final RedissonClient redissonClient;
    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    @Around("@annotation(com.exam.redis.lock.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // 키 생성 (단순화를 위해 파라미터 파싱 로직은 생략하고 어노테이션 값을 그대로 사용)
        // 실무에서는 SpEL Parser를 써서 "#id" 같은 값을 파싱해야 함
        String key = REDISSON_LOCK_PREFIX + distributedLock.key();
        
        RLock rLock = redissonClient.getLock(key);

        try {
            boolean available = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
            
            if (!available) {
                log.warn("락 획득 실패: {}", key);
                return false; // 또는 예외 던지기
            }

            log.info("락 획득 성공: {}", key);
            return joinPoint.proceed();
        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            try {
                rLock.unlock();
                log.info("락 해제 성공: {}", key);
            } catch (IllegalMonitorStateException e) {
                log.info("락이 이미 해제되었거나, 다른 스레드가 점유 중입니다.");
            }
        }
    }
}
