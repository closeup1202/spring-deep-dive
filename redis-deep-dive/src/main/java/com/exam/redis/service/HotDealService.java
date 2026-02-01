package com.exam.redis.service;

import com.exam.redis.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HotDealService {

    // 분산 락 적용: 동시에 여러 서버/스레드에서 호출해도 한 번에 하나만 실행됨
    @DistributedLock(key = "hot-deal-item-1")
    public void purchaseItem(String userId) {
        log.info("구매 로직 진입 - User: {}", userId);
        try {
            Thread.sleep(1000); // 로직 수행 시간 시뮬레이션
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("구매 완료 - User: {}", userId);
    }
}
