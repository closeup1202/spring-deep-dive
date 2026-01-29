package com.exam.lock.service;

import com.exam.lock.domain.Stock;
import com.exam.lock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptimisticLockStockService {

    private final StockRepository stockRepository;

    // 부모 트랜잭션이 있더라도 무조건 새로운 트랜잭션을 생성하여 실행 (재시도 시 안전성 확보)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrease(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id)
                .orElseThrow();
        
        stock.decrease(quantity);
    }
}
