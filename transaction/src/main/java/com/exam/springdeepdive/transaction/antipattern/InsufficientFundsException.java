package com.exam.springdeepdive.transaction.antipattern;

/**
 * 체크 예외 예제: 잔액 부족 예외
 */
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
