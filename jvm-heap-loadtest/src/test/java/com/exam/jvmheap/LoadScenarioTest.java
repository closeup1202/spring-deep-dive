package com.exam.jvmheap;

import com.exam.jvmheap.loadtest.LoadGenerator;
import com.exam.jvmheap.loadtest.LoadResult;
import com.exam.jvmheap.loadtest.LoadSpec;
import com.exam.jvmheap.memory.Bytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제로 HTTP 부하를 넣고 힙이 어떻게 반응하는지 본다.
 *
 * <p>일반 test 태스크에서는 제외된다 (느리고 머신 상태에 좌우된다).
 * <pre>
 *   ./gradlew :jvm-heap-loadtest:loadTest
 * </pre>
 *
 * <p><b>주의</b>: 부하 생성기와 서버가 같은 JVM 안에서 돈다.
 * CPU 를 나눠 쓰고 GC 도 공유하므로 절대 수치는 신뢰하지 말 것.
 * 여기서 얻는 것은 절대 성능이 아니라 <b>시나리오 간 상대 비교</b>다.
 */
@Tag("load")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadScenarioTest {

    @LocalServerPort
    private int port;

    @Autowired
    private com.exam.jvmheap.memory.LeakySessionStore leakySessionStore;

    private LoadGenerator generator() {
        return new LoadGenerator("http://localhost:" + port);
    }

    @Test
    @DisplayName("1. 베이스라인 - 정상 워크로드의 기준선을 만든다")
    void baseline() {
        LoadResult result = generator().run(
                LoadSpec.of("baseline", "POST", "/api/orders?items=20", 10, Duration.ofSeconds(5)));

        System.out.println(result.report());

        assertThat(result.errorRate()).as("정상 워크로드에서 에러는 0 이어야 한다").isZero();
        assertThat(result.tps()).isPositive();

        // 여기서 나온 p95 가 앞으로 모든 비교의 기준이다.
        // 튜닝의 첫 단계는 항상 "고치기 전 숫자"를 남기는 것이다.
    }

    @Test
    @DisplayName("2. 포화 - 동시 요청이 스레드 수를 넘으면 응답시간이 무너진다")
    void saturation() {
        // 서버는 처리에 300ms 를 쓴다. Tomcat 스레드는 20개 (application.yml).
        // 동시 40건을 넣으면 절반은 큐에서 기다린다 -> 지연은 300ms 를 훨씬 넘긴다.
        LoadResult result = generator().run(
                LoadSpec.of("saturation", "GET", "/lab/blocking?millis=300&bufferKb=512", 40,
                        Duration.ofSeconds(5)).withWarmup(Duration.ofSeconds(1)));

        System.out.println(result.report());
        System.out.println("""
                해석:
                  - 서버 처리시간은 300ms 고정인데 p95 가 그보다 크면 그 차이가 '대기시간'이다.
                  - 이때 CPU 는 한가하다. 병목은 연산이 아니라 스레드 수다.
                  - 요청당 512KB x 동시 처리 수만큼 힙이 상주한다는 것도 같이 확인할 것.
                """);

        assertThat(result.meanMillis())
                .as("서버가 300ms 를 쓰므로 응답은 최소 그 이상이다")
                .isGreaterThanOrEqualTo(290);
        assertThat(result.percentileMillis(95))
                .as("포화 상태에서는 p95 가 처리시간보다 확실히 크다")
                .isGreaterThan(result.percentileMillis(50) * 0.9);
    }

    @Test
    @DisplayName("3. 부하 중 누수 - 저점이 우상향하면 그것이 누수다")
    void leakUnderLoad() {
        leakySessionStore.clear();

        // 요청 하나가 10 x 50KB = 500KB 를 영구히 보관한다.
        // think time 을 줘서 테스트 JVM 힙(512m)을 터뜨리지 않는 범위로 제어한다.
        LoadResult result = generator().run(
                new LoadSpec("leak-under-load", "POST", "/lab/leak?count=10&payloadKb=50",
                        2, Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(50)));

        System.out.println(result.report());
        System.out.printf("보관 중인 세션: %d개 / %s%n",
                leakySessionStore.size(), Bytes.human(leakySessionStore.approximateBytes()));

        assertThat(result.heapAfter().usedDeltaFrom(result.heapBefore()))
                .as("측정 구간 동안 힙 사용량이 순증한다")
                .isPositive();
        assertThat(leakySessionStore.size()).isPositive();

        System.out.println("""
                해석:
                  - baseline 시나리오는 요청이 끝나면 힙이 제자리로 돌아온다.
                  - 이 시나리오는 요청 수에 비례해 힙이 계속 올라간다.
                  - 부하 테스트를 짧게(1~2분) 돌리면 이 차이가 안 보인다.
                    누수는 soak(내구성) 테스트로만 잡힌다.
                """);

        leakySessionStore.clear();
    }

    @Test
    @DisplayName("4. 동시성을 올리며 한계점을 찾는다 (capacity test)")
    void rampUp() {
        LoadGenerator generator = generator();
        LoadSpec base = LoadSpec.of("ramp", "POST", "/api/orders?items=20", 1, Duration.ofSeconds(3))
                .withWarmup(Duration.ofSeconds(1));

        System.out.println("동시성 | TPS      | p95(ms) | GC pause(ms)");
        System.out.println("------|----------|---------|-------------");
        double previousTps = 0;
        for (int concurrency : new int[]{1, 4, 16, 64}) {
            LoadResult result = generator.run(base.withConcurrency(concurrency));
            System.out.printf("%6d| %8.1f | %7.1f | %d%n",
                    concurrency, result.tps(), result.percentileMillis(95), result.gc().pauseMillis());

            assertThat(result.tps()).isPositive();
            previousTps = result.tps();
        }
        assertThat(previousTps).isPositive();

        System.out.println("""
                해석:
                  - 동시성을 올려도 TPS 가 더 이상 늘지 않는 지점이 그 서버의 처리 한계다.
                  - 그 지점을 넘기면 TPS 는 그대로인데 응답시간만 선형으로 늘어난다.
                    (= 큐가 길어질 뿐 처리량은 그대로)
                  - 용량 산정은 이 한계점의 60~70% 를 운영 목표로 잡는 것에서 시작한다.
                """);
    }
}
