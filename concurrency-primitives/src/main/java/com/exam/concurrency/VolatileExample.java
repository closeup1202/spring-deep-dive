package com.exam.concurrency;

import lombok.extern.slf4j.Slf4j;

/**
 * volatile 키워드 예제
 *
 * volatile의 핵심:
 * 1. 가시성(Visibility) 보장: 한 스레드가 쓴 값을 다른 스레드가 즉시 볼 수 있음
 * 2. 재배치(Reordering) 방지: 컴파일러/CPU의 명령어 재배치 방지
 * 3. 원자성(Atomicity) 보장 안함: 읽기/쓰기는 원자적이지만, 복합 연산(read-modify-write)은 아님
 */
@Slf4j
public class VolatileExample {

    /**
     * 시나리오 1: volatile 없이 플래그 사용 (문제 발생 가능)
     */
    static class WithoutVolatile {
        private boolean running = true; // volatile 없음

        public void start() {
            new Thread(() -> {
                log.info("Worker thread started");
                int counter = 0;
                // 메인 스레드가 running을 false로 변경해도
                // CPU 캐시에 있는 값(true)을 계속 읽을 수 있음
                while (running) {
                    counter++;
                }
                log.info("Worker thread stopped after {} iterations", counter);
            }).start();
        }

        public void stop() {
            log.info("Stopping worker thread...");
            running = false; // 메인 스레드가 변경
        }

        public boolean isRunning() {
            return running;
        }
    }

    /**
     * 시나리오 2: volatile로 플래그 사용 (올바른 동작)
     */
    static class WithVolatile {
        private volatile boolean running = true; // volatile 사용

        public void start() {
            new Thread(() -> {
                log.info("Worker thread started (with volatile)");
                int counter = 0;
                // volatile 덕분에 메인 메모리에서 최신 값을 읽음
                while (running) {
                    counter++;
                }
                log.info("Worker thread stopped (with volatile) after {} iterations", counter);
            }).start();
        }

        public void stop() {
            log.info("Stopping worker thread (with volatile)...");
            running = false;
        }

        public boolean isRunning() {
            return running;
        }
    }

    /**
     * 시나리오 3: volatile은 복합 연산에 대한 원자성을 보장하지 않음
     */
    static class VolatileNotAtomic {
        private volatile int counter = 0;

        // counter++는 3단계 연산:
        // 1. 읽기 (read)
        // 2. 증가 (modify)
        // 3. 쓰기 (write)
        // volatile은 각 단계의 가시성은 보장하지만, 전체 연산의 원자성은 보장 안함
        public void increment() {
            counter++; // NOT thread-safe!
        }

        public int getCounter() {
            return counter;
        }
    }

    /**
     * 시나리오 4: Double-Checked Locking with volatile (Singleton 패턴)
     */
    static class VolatileSingleton {
        // volatile 없으면 부분적으로 초기화된 객체를 볼 수 있음
        private static volatile VolatileSingleton instance;

        private VolatileSingleton() {
            log.info("Singleton instance created");
        }

        public static VolatileSingleton getInstance() {
            if (instance == null) { // 첫 번째 체크 (동기화 없음)
                synchronized (VolatileSingleton.class) {
                    if (instance == null) { // 두 번째 체크 (동기화 있음)
                        instance = new VolatileSingleton();
                    }
                }
            }
            return instance;
        }
    }

    /**
     * 시나리오 5: volatile과 happens-before 관계
     *
     * volatile 변수에 대한 쓰기 이전의 모든 메모리 작업은
     * 그 변수를 읽는 모든 스레드에게 보임
     */
    static class HappensBeforeExample {
        private int normalVariable = 0;
        private volatile boolean ready = false;

        public void writer() {
            normalVariable = 42;  // 1. 일반 변수 쓰기
            ready = true;         // 2. volatile 변수 쓰기 (happens-before edge 생성)
        }

        public int reader() {
            if (ready) {          // 3. volatile 변수 읽기
                return normalVariable; // 4. 일반 변수 읽기 (항상 42를 봄)
            }
            return -1;
        }
    }

    /**
     * 시나리오 6: volatile long/double (64비트 변수)
     *
     * Java에서 long과 double은 64비트이고,
     * volatile 없이는 읽기/쓰기가 원자적이지 않을 수 있음 (32비트씩 2번)
     */
    static class VolatileLongDouble {
        private volatile long volatileLong = 0L;
        private long normalLong = 0L;

        // volatile long은 읽기/쓰기가 원자적
        public void setVolatileLong(long value) {
            volatileLong = value;
        }

        public long getVolatileLong() {
            return volatileLong;
        }

        // 일반 long은 2개의 32비트 연산으로 나뉠 수 있음
        // 한 스레드가 쓰는 중에 다른 스레드가 읽으면 이상한 값을 볼 수 있음
        public void setNormalLong(long value) {
            normalLong = value;
        }

        public long getNormalLong() {
            return normalLong;
        }
    }

    /**
     * 시나리오 7: Producer-Consumer with volatile flag
     */
    static class ProducerConsumer {
        private volatile boolean dataReady = false;
        private int data = 0;

        public void produce(int value) {
            data = value;          // 1. 데이터 쓰기
            dataReady = true;      // 2. volatile 플래그 설정 (happens-before)
            log.info("Produced: {}", value);
        }

        public Integer consume() {
            if (dataReady) {       // 3. volatile 플래그 확인
                int value = data;  // 4. 데이터 읽기 (항상 최신 값)
                log.info("Consumed: {}", value);
                return value;
            }
            return null;
        }
    }
}
