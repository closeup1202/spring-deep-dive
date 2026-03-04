package com.exam.txtemplate.domain;

import com.exam.txtemplate.exception.InsufficientBalanceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @ToString.Include
    private String owner;

    @ToString.Include
    private long balance;

    /**
     * 낙관적 락 버전 컬럼.
     * 동시 수정 시 ObjectOptimisticLockingFailureException 발생 → RetryTemplate이 감지해 재시도.
     */
    @Version
    private Long version;

    public static Account of(String owner, long initialBalance) {
        Account account = new Account();
        account.owner = owner;
        account.balance = initialBalance;
        return account;
    }

    public void withdraw(long amount) {
        if (this.balance < amount) {
            throw new InsufficientBalanceException(
                "잔액 부족: 현재 잔액=%d, 출금 요청=%d".formatted(this.balance, amount)
            );
        }
        this.balance -= amount;
    }

    public void deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("입금액은 0보다 커야 합니다: " + amount);
        }
        this.balance += amount;
    }
}
