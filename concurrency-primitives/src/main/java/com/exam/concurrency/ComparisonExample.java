package com.exam.concurrency;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * synchronized vs volatile vs Atomic 비교
 *
 * 각 메커니즘의 특성과 사용 시나리오를 비교
 */
@Slf4j
public class ComparisonExample {

    /**
     * 1. synchronized - 전통적인 동기화 (가장 강력하지만 무거움)
     */
    static class SynchronizedCounter {
        private int counter = 0;

        // synchronized 메서드: 메서드 전체를 동기화
        public synchronized void increment() {
            counter++; // 읽기-수정-쓰기 연산이 원자적
        }

        public synchronized int getCounter() {
            return counter;
        }

        // synchronized 블록: 필요한 부분만 동기화
        public void incrementWithBlock() {
            synchronized (this) {
                counter++;
            }
        }

        /**
         * 장점:
         * - 모든 종류의 복합 연산 보호 가능
         * - 여러 변수에 대한 연산을 하나의 임계 영역으로 보호
         * - wait/notify를 통한 스레드 간 협력 가능
         *
         * 단점:
         * - 락 획득/해제 오버헤드
         * - 다른 스레드 블로킹 (대기 상태로 전환)
         * - 데드락 가능성
         */
    }

    /**
     * 2. volatile - 가시성만 보장 (가장 가벼움)
     */
    static class VolatileCounter {
        private volatile int counter = 0;

        // WARNING: 스레드 안전하지 않음!
        public void increment() {
            counter++; // 원자적이지 않음: 읽기 → 증가 → 쓰기
        }

        public int getCounter() {
            return counter; // 읽기는 항상 최신 값
        }

        /**
         * 장점:
         * - 가장 가벼움 (락 없음)
         * - 항상 최신 값 보장
         * - 단순 읽기/쓰기는 원자적
         *
         * 단점:
         * - 복합 연산(++, --, +=)은 원자적이지 않음
         * - 단일 변수에만 적용
         */
    }

    /**
     * 3. Atomic - Lock-free 원자 연산 (성능과 안전성 균형)
     */
    static class AtomicCounter {
        private final AtomicInteger counter = new AtomicInteger(0);

        // CAS(Compare-And-Swap) 기반 원자 연산
        public void increment() {
            counter.incrementAndGet(); // 원자적
        }

        public int getCounter() {
            return counter.get();
        }

        /**
         * 장점:
         * - 복합 연산도 원자적 (CAS 알고리즘)
         * - 락 없음 (Lock-free)
         * - synchronized보다 일반적으로 빠름
         * - 블로킹 없음 (대기 상태로 전환 안됨)
         *
         * 단점:
         * - 단일 변수에만 적용
         * - 높은 경합 시 CAS 재시도 오버헤드
         * - 복잡한 임계 영역에는 부적합
         */
    }

    /**
     * 시나리오 비교 1: 단순 플래그
     */
    static class FlagComparison {
        // 1. synchronized 방식 - 과도함
        static class SynchronizedFlag {
            private boolean flag = false;
            public synchronized void setFlag(boolean value) { flag = value; }
            public synchronized boolean getFlag() { return flag; }
        }

        // 2. volatile 방식 - 적절함 ✅
        static class VolatileFlag {
            private volatile boolean flag = false;
            public void setFlag(boolean value) { flag = value; }
            public boolean getFlag() { return flag; }
        }

        // 3. AtomicBoolean 방식 - CAS 필요 시
        static class AtomicFlag {
            private final java.util.concurrent.atomic.AtomicBoolean flag =
                new java.util.concurrent.atomic.AtomicBoolean(false);
            public void setFlag(boolean value) { flag.set(value); }
            public boolean getFlag() { return flag.get(); }
            public boolean compareAndSet(boolean expect, boolean update) {
                return flag.compareAndSet(expect, update);
            }
        }
    }

    /**
     * 시나리오 비교 2: 카운터
     */
    static class CounterComparison {
        // 1. synchronized - 안전하지만 느림
        static class SyncCounter {
            private int count = 0;
            public synchronized void increment() { count++; }
            public synchronized int get() { return count; }
        }

        // 2. volatile - 스레드 안전하지 않음 ❌
        static class VolatileCounter {
            private volatile int count = 0;
            public void increment() { count++; } // UNSAFE!
            public int get() { return count; }
        }

        // 3. Atomic - 안전하고 빠름 ✅
        static class AtomCounter {
            private final AtomicInteger count = new AtomicInteger(0);
            public void increment() { count.incrementAndGet(); }
            public int get() { return count.get(); }
        }
    }

    /**
     * 시나리오 비교 3: 여러 변수의 일관성
     */
    static class MultiVariableComparison {
        // 1. synchronized - 적절함 ✅
        static class SynchronizedAccount {
            private int balance = 0;
            private int transactions = 0;

            public synchronized void deposit(int amount) {
                balance += amount;
                transactions++;
                // 두 변수가 일관성 있게 업데이트됨
            }

            public synchronized int getBalance() { return balance; }
            public synchronized int getTransactions() { return transactions; }
        }

        // 2. Atomic - 일관성 보장 안됨 ❌
        static class AtomicAccount {
            private final AtomicInteger balance = new AtomicInteger(0);
            private final AtomicInteger transactions = new AtomicInteger(0);

            public void deposit(int amount) {
                balance.addAndGet(amount);
                // 다른 스레드가 여기서 읽을 수 있음!
                transactions.incrementAndGet();
                // 두 변수 사이의 일관성이 깨질 수 있음
            }

            public int getBalance() { return balance.get(); }
            public int getTransactions() { return transactions.get(); }
        }
    }

    /**
     * 성능 비교 가이드
     *
     * 경합이 낮을 때 (Low Contention):
     * Atomic > volatile > synchronized
     *
     * 경합이 높을 때 (High Contention):
     * - 단순 연산: LongAdder > Atomic > synchronized
     * - 복잡한 연산: synchronized (락이 오히려 효율적)
     *
     * 메모리 오버헤드:
     * volatile (0) < Atomic (객체 생성) < synchronized (모니터 객체)
     */

    /**
     * 선택 가이드
     *
     * volatile 사용:
     * - 단순 플래그 (boolean)
     * - 읽기가 쓰기보다 훨씬 많을 때
     * - 하나의 스레드만 쓰고, 여러 스레드가 읽을 때
     * - long/double의 원자적 읽기/쓰기가 필요할 때
     *
     * Atomic 사용:
     * - 단일 변수에 대한 복합 연산 (++, --, CAS)
     * - Lock-free 알고리즘 구현
     * - 카운터, 시퀀스 생성기
     * - 경합이 낮거나 중간 정도일 때
     *
     * synchronized 사용:
     * - 여러 변수의 일관성 유지
     * - 복잡한 임계 영역
     * - wait/notify가 필요한 경우
     * - 경합이 매우 높을 때 (단순하게 관리)
     */

    /**
     * 메모리 가시성 비교
     */
    static class VisibilityComparison {
        // 1. 일반 변수 - 가시성 보장 없음
        private int normalVar = 0;

        // 2. volatile - 가시성 보장
        private volatile int volatileVar = 0;

        // 3. Atomic - 가시성 보장 (내부적으로 volatile 사용)
        private final AtomicInteger atomicVar = new AtomicInteger(0);

        // 4. synchronized - 가시성 보장 (모니터 진입/탈출 시)
        private int syncVar = 0;
        public synchronized void setSyncVar(int value) { syncVar = value; }
        public synchronized int getSyncVar() { return syncVar; }
    }

    /**
     * Happens-Before 관계 비교
     *
     * 1. volatile 쓰기/읽기
     *    쓰기 이전의 모든 작업 → volatile 쓰기 → volatile 읽기 → 읽기 이후의 모든 작업
     *
     * 2. synchronized 진입/탈출
     *    모니터 진입 이전 → 임계 영역 → 모니터 탈출 이후
     *
     * 3. Atomic 연산
     *    Atomic 쓰기 이전 → Atomic 연산 → Atomic 읽기 이후
     */
}
