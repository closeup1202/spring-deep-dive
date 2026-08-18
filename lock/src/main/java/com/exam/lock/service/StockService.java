package com.exam.lock.service;

import com.exam.lock.domain.Stock;
import com.exam.lock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public void decrease(Long id, Long quantity) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow();

        stock.decrease(quantity);
    }
}
