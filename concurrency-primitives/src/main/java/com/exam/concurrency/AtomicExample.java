package com.exam.concurrency;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.*;

/**
 * Atomic 클래스 예제
 *
 * Atomic의 핵심:
 * 1. Lock-free 알고리즘: CAS(Compare-And-Swap) 연산 사용
 * 2. 원자성(Atomicity) 보장: 복합 연산도 원자적으로 수행
 * 3. 가시성(Visibility) 보장: volatile과 동일한 메모리 가시성
 * 4. 성능: synchronized보다 일반적으로 빠름 (경합이 적을 때)
 */
@Slf4j
public class AtomicExample {

    /**
     * 시나리오 1: AtomicInteger - 기본 연산
     */
    static class AtomicIntegerExample {
        private final AtomicInteger counter = new AtomicInteger(0);

        // 원자적 증가
        public int increment() {
            return counter.incrementAndGet(); // i++ 연산을 원자적으로
        }

        // 원자적 감소
        public int decrement() {
            return counter.decrementAndGet(); // i-- 연산을 원자적으로
        }

        // 원자적 덧셈
        public int addAndGet(int delta) {
            return counter.addAndGet(delta);
        }

        // CAS(Compare-And-Set) 연산
        public boolean compareAndSet(int expect, int update) {
            return counter.compareAndSet(expect, update);
        }

        // 현재 값을 반환하고 새 값으로 설정
        public int getAndSet(int newValue) {
            return counter.getAndSet(newValue);
        }

        // 함수형 업데이트 (Java 8+)
        public int updateAndGet(int multiplier) {
            return counter.updateAndGet(current -> current * multiplier);
        }

        public int getCounter() {
            return counter.get();
        }
    }

    /**
     * 시나리오 2: AtomicLong - 64비트 원자 연산
     */
    static class AtomicLongExample {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);

        public void recordRequest(boolean success) {
            totalRequests.incrementAndGet();
            if (success) {
                successfulRequests.incrementAndGet();
            }
        }

        public double getSuccessRate() {
            long total = totalRequests.get();
            if (total == 0) return 0.0;
            return (double) successfulRequests.get() / total * 100;
        }

        public void reset() {
            totalRequests.set(0);
            successfulRequests.set(0);
        }
    }

    /**
     * 시나리오 3: AtomicBoolean - 플래그 제어
     */
    static class AtomicBooleanExample {
        private final AtomicBoolean initialized = new AtomicBoolean(false);

        // 딱 한 번만 실행되는 초기화
        public boolean initializeOnce() {
            if (initialized.compareAndSet(false, true)) {
                log.info("Initializing... (only once)");
                // 초기화 로직
                return true;
            }
            log.info("Already initialized");
            return false;
        }

        public void reset() {
            initialized.set(false);
        }
    }

    /**
     * 시나리오 4: AtomicReference - 객체 참조의 원자적 업데이트
     */
    static class AtomicReferenceExample {
        static class User {
            final String name;
            final int age;

            User(String name, int age) {
                this.name = name;
                this.age = age;
            }

            @Override
            public String toString() {
                return "User{name='" + name + "', age=" + age + "}";
            }
        }

        private final AtomicReference<User> currentUser = new AtomicReference<>(
            new User("Unknown", 0)
        );

        // 원자적으로 사용자 변경
        public void updateUser(String name, int age) {
            User newUser = new User(name, age);
            currentUser.set(newUser);
        }

        // CAS로 조건부 업데이트
        public boolean updateIfMatches(User expected, User newUser) {
            return currentUser.compareAndSet(expected, newUser);
        }

        // 함수형 업데이트
        public User incrementAge() {
            return currentUser.updateAndGet(user -> new User(user.name, user.age + 1));
        }

        public User getCurrentUser() {
            return currentUser.get();
        }
    }

    /**
     * 시나리오 5: AtomicIntegerArray - 배열 요소의 원자적 업데이트
     */
    static class AtomicIntegerArrayExample {
        private final AtomicIntegerArray counters = new AtomicIntegerArray(10);

        // 특정 인덱스의 카운터 증가
        public int incrementCounter(int index) {
            return counters.incrementAndGet(index);
        }

        // 특정 인덱스의 값을 원자적으로 더하기
        public int addToCounter(int index, int delta) {
            return counters.addAndGet(index, delta);
        }

        // 모든 카운터의 합계
        public int getTotal() {
            int sum = 0;
            for (int i = 0; i < counters.length(); i++) {
                sum += counters.get(i);
            }
            return sum;
        }
    }

    /**
     * 시나리오 6: AtomicStampedReference - ABA 문제 해결
     *
     * ABA 문제: 값이 A → B → A로 변경되었을 때, CAS는 이를 감지하지 못함
     * AtomicStampedReference는 stamp(버전)를 함께 관리하여 해결
     */
    static class AtomicStampedReferenceExample {
        static class Account {
            final String id;
            final int balance;

            Account(String id, int balance) {
                this.id = id;
                this.balance = balance;
            }

            @Override
            public String toString() {
                return "Account{id='" + id + "', balance=" + balance + "}";
            }
        }

        private final AtomicStampedReference<Account> accountRef =
            new AtomicStampedReference<>(new Account("ACC001", 1000), 0);

        public boolean updateBalance(int newBalance) {
            int[] stampHolder = new int[1];
            Account currentAccount = accountRef.get(stampHolder);
            int currentStamp = stampHolder[0];

            Account newAccount = new Account(currentAccount.id, newBalance);
            int newStamp = currentStamp + 1;

            boolean success = accountRef.compareAndSet(
                currentAccount, newAccount,
                currentStamp, newStamp
            );

            if (success) {
                log.info("Updated balance to {} (stamp: {})", newBalance, newStamp);
            }
            return success;
        }

        public Account getAccount() {
            return accountRef.getReference();
        }

        public int getStamp() {
            return accountRef.getStamp();
        }
    }

    /**
     * 시나리오 7: LongAdder - 고성능 카운터 (경합이 심할 때)
     *
     * AtomicLong vs LongAdder:
     * - AtomicLong: 단일 값, 높은 경합 시 성능 저하
     * - LongAdder: 내부적으로 여러 셀로 분산, 높은 경합 시 성능 우수
     */
    static class LongAdderExample {
        private final LongAdder counter = new LongAdder();

        // 증가 (내부적으로 여러 셀 중 하나에 누적)
        public void increment() {
            counter.increment();
        }

        public void add(long value) {
            counter.add(value);
        }

        // 전체 합계 (모든 셀의 합)
        public long sum() {
            return counter.sum();
        }

        // 합계 후 리셋
        public long sumThenReset() {
            return counter.sumThenReset();
        }

        public void reset() {
            counter.reset();
        }
    }

    /**
     * 시나리오 8: LongAccumulator - 일반화된 누산기
     */
    static class LongAccumulatorExample {
        // 최댓값을 추적하는 누산기
        private final LongAccumulator maxTracker = new LongAccumulator(Long::max, Long.MIN_VALUE);

        // 최솟값을 추적하는 누산기
        private final LongAccumulator minTracker = new LongAccumulator(Long::min, Long.MAX_VALUE);

        public void record(long value) {
            maxTracker.accumulate(value);
            minTracker.accumulate(value);
        }

        public long getMax() {
            return maxTracker.get();
        }

        public long getMin() {
            return minTracker.get();
        }

        public void reset() {
            maxTracker.reset();
            minTracker.reset();
        }
    }

    /**
     * 시나리오 9: DoubleAdder / DoubleAccumulator - double 타입 지원
     */
    static class DoubleAdderExample {
        private final DoubleAdder totalAmount = new DoubleAdder();

        public void addAmount(double amount) {
            totalAmount.add(amount);
        }

        public double getTotal() {
            return totalAmount.sum();
        }

        public void reset() {
            totalAmount.reset();
        }
    }

    /**
     * 시나리오 10: AtomicMarkableReference - boolean 마크 사용
     */
    static class AtomicMarkableReferenceExample {
        static class Task {
            final String name;
            Task(String name) { this.name = name; }
            @Override
            public String toString() { return "Task{" + name + "}"; }
        }

        // 작업과 완료 여부를 원자적으로 관리
        private final AtomicMarkableReference<Task> taskRef =
            new AtomicMarkableReference<>(null, false);

        public boolean assignTask(Task task) {
            boolean success = taskRef.compareAndSet(null, task, false, false);
            if (success) {
                log.info("Task assigned: {}", task);
            }
            return success;
        }

        public boolean completeTask(Task expectedTask) {
            boolean[] markHolder = new boolean[1];
            Task currentTask = taskRef.get(markHolder);

            if (currentTask != null && currentTask.equals(expectedTask) && !markHolder[0]) {
                boolean success = taskRef.compareAndSet(currentTask, currentTask, false, true);
                if (success) {
                    log.info("Task completed: {}", currentTask);
                }
                return success;
            }
            return false;
        }

        public boolean isTaskCompleted() {
            return taskRef.isMarked();
        }
    }
}
