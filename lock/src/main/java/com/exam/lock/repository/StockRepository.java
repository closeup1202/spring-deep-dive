package com.exam.lock.repository;

import com.exam.lock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    // 비관적 락 (PESSIMISTIC_WRITE -> SELECT ... FOR UPDATE)
    // DB단에서 Row에 락을 걸어 다른 트랜잭션의 접근을 막음
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Optional<Stock> findByIdWithPessimisticLock(@Param("id") Long id);

    // 낙관적 락 (OPTIMISTIC)
    // 실제 DB 락을 걸지 않고, 조회 시점의 version과 수정 시점의 version을 비교
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select s from Stock s where s.id = :id")
    Optional<Stock> findByIdWithOptimisticLock(@Param("id") Long id);
}
