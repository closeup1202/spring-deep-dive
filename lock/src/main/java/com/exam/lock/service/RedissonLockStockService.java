package com.exam.lock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonLockStockService {

    private final RedissonClient redissonClient;
    private final StockService stockService;

    public void decrease(Long id, Long quantity) {
        RLock lock = redissonClient.getLock("stock_lock_" + id);

        try {
            // 락 획득 시도 (wait time: 10s, lease time: 1s)
            // 10초 동안 락 획득을 시도하고, 획득 후 1초가 지나면 자동으로 락 해제
            boolean available = lock.tryLock(10, 1, TimeUnit.SECONDS);

            if (!available) {
                log.info("락 획득 실패");
                return;
            }

            // 락 획득 성공 후 비즈니스 로직 수행
            // 트랜잭션은 락 내부에서 시작하고 끝내야 함 (데이터 정합성 보장)
            // 여기 안에서 트랜잭션 시작 → COMMIT까지 완료
            // @Transactional을 decrease()에 바로 붙이면 트랜잭션은 메서드 바깥에서 시작하고 끝남
            // 그래서 메서드 안에서 unlock()을 해도 DB 커밋은 아직 안 된 상태일 수 있음
            // 그 순간 다른 서버가 락을 획득하면, 아직 반영되지 않은 DB 데이터를 읽을 가능성이 생김
            // 따라서, DB 트랜잭션이 완전히 커밋된 뒤에 Redis 락을 해제해야 안전
            // 보통 [락을 담당하는 서비스]와 [@Transactional 비즈니스 서비스]를 분리해.
            stockService.decrease(id, quantity);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 락 해제
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
