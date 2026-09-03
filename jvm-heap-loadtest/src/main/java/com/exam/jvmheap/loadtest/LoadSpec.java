package com.exam.jvmheap.loadtest;

import java.time.Duration;

/**
 * 부하 시나리오 하나의 정의.
 *
 * <p>부하 테스트는 "몇 명이 몇 초 동안 무엇을 얼마나 자주 하는가" 가 전부다.
 * 이 네 가지를 record 로 고정해두면 시나리오 간 비교가 가능해진다.
 *
 * @param name        시나리오 이름 (리포트에 찍힌다)
 * @param method      HTTP 메서드
 * @param path        대상 경로 (baseUrl 은 실행 시점에 붙는다)
 * @param concurrency 동시 사용자 수 (= 워커 스레드 수)
 * @param warmup      측정에서 제외할 준비 구간. JIT 컴파일과 커넥션 풀 예열에 필요하다
 * @param duration    측정 구간
 * @param thinkTime   요청 사이의 대기. 0 이면 "최대한 빨리" 던진다
 */
public record LoadSpec(
        String name,
        String method,
        String path,
        int concurrency,
        Duration warmup,
        Duration duration,
        Duration thinkTime
) {

    public static LoadSpec of(String name, String method, String path, int concurrency, Duration duration) {
        return new LoadSpec(name, method, path, concurrency, Duration.ofSeconds(3), duration, Duration.ZERO);
    }

    public LoadSpec withConcurrency(int newConcurrency) {
        return new LoadSpec(name, method, path, newConcurrency, warmup, duration, thinkTime);
    }

    public LoadSpec withThinkTime(Duration newThinkTime) {
        return new LoadSpec(name, method, path, concurrency, warmup, duration, newThinkTime);
    }

    public LoadSpec withWarmup(Duration newWarmup) {
        return new LoadSpec(name, method, path, concurrency, newWarmup, duration, thinkTime);
    }
}
