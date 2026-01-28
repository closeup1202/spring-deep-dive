package com.exam.lock.service;

import com.exam.lock.domain.Stock;
import com.exam.lock.repository.StockRepository;
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
    private final StockRepository stockRepository;

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
            Stock stock = stockRepository.findById(id).orElseThrow();
            stock.decrease(quantity);
            stockRepository.save(stock);

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
