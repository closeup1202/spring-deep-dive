package com.exam.lock.service;

import com.exam.lock.domain.Stock;
import com.exam.lock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticLockStockService {

    private final StockRepository stockRepository;

    @Transactional
    public void decrease(Long id, Long quantity) {
        // SELECT ... FOR UPDATE 쿼리가 실행됨
        // 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 대기함
        Stock stock = stockRepository.findByIdWithPessimisticLock(id)
                .orElseThrow();

        stock.decrease(quantity);
    }
}
