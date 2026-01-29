package com.exam.lock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OptimisticLockStockFacade {

    private final OptimisticLockStockService optimisticLockStockService;

    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (true) {
            try {
                // 프록시를 통해 호출하므로 @Transactional이 정상 동작함
                optimisticLockStockService.decrease(id, quantity);
                
                // 성공하면 루프 종료
                break;
            } catch (ObjectOptimisticLockingFailureException e) {
                // 충돌 발생 시 50ms 대기 후 재시도
                Thread.sleep(50);
            }
        }
    }
}
