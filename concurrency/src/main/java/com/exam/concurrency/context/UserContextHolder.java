package com.exam.concurrency.context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserContextHolder {

    // 스레드마다 별도의 저장소를 가짐
    private static final ThreadLocal<String> userHolder = new ThreadLocal<>();

    public static void set(String username) {
        log.info("[ContextHolder] Setting user: {} for thread: {}", username, Thread.currentThread().getName());
        userHolder.set(username);
    }

    public static String get() {
        return userHolder.get();
    }

    public static void clear() {
        log.info("[ContextHolder] Clearing user for thread: {}", Thread.currentThread().getName());
        userHolder.remove(); // 중요: 스레드 풀 환경에서는 반드시 지워줘야 함 (메모리 누수 및 데이터 오염 방지)
    }
}