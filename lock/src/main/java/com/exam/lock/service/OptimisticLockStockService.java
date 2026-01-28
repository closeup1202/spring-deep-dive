package com.exam.lock.service;

import com.exam.lock.domain.Stock;
import com.exam.lock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptimisticLockStockService {

    private final StockRepository stockRepository;

    @Transactional
    public void decrease(Long id, Long quantity) {
        try {
            // 조회 시점의 version과 수정 시점의 version을 비교하여
            // 다르면 ObjectOptimisticLockingFailureException 발생
            Stock stock = stockRepository.findByIdWithOptimisticLock(id)
                    .orElseThrow();
            stock.decrease(quantity);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 충돌 발생 시 재시도 로직 (예: 100ms 대기 후 다시 시도)
            // 실무에서는 재시도 횟수 제한, 백오프 전략 등을 추가해야 함
            try {
                Thread.sleep(100);
                decrease(id, quantity);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
