package com.exam.jvmheap;

import com.exam.jvmheap.loadtest.LoadResult;
import com.exam.jvmheap.memory.GcProbe;
import com.exam.jvmheap.memory.HeapProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 부하 테스트 리포트의 숫자가 무엇을 의미하는지 계산으로 확인한다.
 *
 * <p>도구가 뱉는 숫자를 그대로 믿기 전에, 그 숫자가 어떻게 만들어지는지 알아야 한다.
 * 특히 평균이 얼마나 쉽게 거짓말을 하는지 보는 것이 이 테스트의 목적이다.
 */
class LoadResultMathTest {

    @Test
    @DisplayName("평균은 느린 요청을 숨긴다 - 그래서 SLO 는 백분위수로 잡는다")
    void averageHidesTheTail() {
        // 99건은 10ms, 1건은 2000ms
        long[] latenciesMicros = new long[100];
        for (int i = 0; i < 99; i++) {
            latenciesMicros[i] = 10_000;
        }
        latenciesMicros[99] = 2_000_000;

        LoadResult result = result(latenciesMicros, 0, 1000);

        assertThat(result.meanMillis())
                .as("평균은 29.9ms - 아무 문제 없어 보인다")
                .isCloseTo(29.9, within(0.5));
        assertThat(result.percentileMillis(50)).isEqualTo(10.0);
        assertThat(result.percentileMillis(99))
                .as("p99 는 여전히 10ms - 100건 중 1건은 p99 로도 안 잡힌다")
                .isEqualTo(10.0);
        assertThat(result.maxMillis())
                .as("max 는 2초 - 실제로 2초를 기다린 사용자가 존재한다")
                .isEqualTo(2000.0);
    }

    @Test
    @DisplayName("백분위수는 정렬된 지연 배열의 위치값이다")
    void percentileIsAPosition() {
        long[] latenciesMicros = new long[100];
        for (int i = 0; i < 100; i++) {
            latenciesMicros[i] = (i + 1) * 1000L;      // 1ms ~ 100ms
        }

        LoadResult result = result(latenciesMicros, 0, 1000);

        assertThat(result.percentileMillis(50)).isEqualTo(50.0);
        assertThat(result.percentileMillis(90)).isEqualTo(90.0);
        assertThat(result.percentileMillis(95)).isEqualTo(95.0);
        assertThat(result.percentileMillis(99)).isEqualTo(99.0);
    }

    @Test
    @DisplayName("TPS 와 에러율")
    void throughputAndErrorRate() {
        long[] latenciesMicros = new long[900];
        java.util.Arrays.fill(latenciesMicros, 5_000);

        LoadResult result = result(latenciesMicros, 100, 3000);

        assertThat(result.total()).isEqualTo(1000);
        assertThat(result.tps())
                .as("성공 900건 / 3초 = 300 TPS")
                .isCloseTo(300.0, within(0.1));
        assertThat(result.errorRate())
                .as("에러 100 / 전체 1000 = 10%")
                .isCloseTo(0.10, within(0.001));
    }

    @Test
    @DisplayName("리틀의 법칙: 필요한 동시 처리 수 = TPS x 응답시간")
    void littlesLaw() {
        double targetTps = 200;
        double responseSeconds = 0.5;

        double requiredConcurrency = targetTps * responseSeconds;

        assertThat(requiredConcurrency).isEqualTo(100.0);

        // 톰캣 스레드가 20개뿐이라면 80건은 큐에서 대기한다.
        // 이때 사용자가 겪는 시간은 처리시간(0.5s)이 아니라 대기시간이 더해진 값이 된다.
        int tomcatThreads = 20;
        assertThat(requiredConcurrency)
                .as("스레드풀이 부족하면 응답시간이 무너진다")
                .isGreaterThan(tomcatThreads);

        // 반대로 응답시간을 0.05초로 줄이면 같은 TPS 를 10개 스레드로 감당한다.
        assertThat(targetTps * 0.05).isEqualTo(10.0);
    }

    private LoadResult result(long[] latenciesMicros, long errors, long elapsedMillis) {
        return new LoadResult("unit", latenciesMicros, errors,
                Map.of(200, (long) latenciesMicros.length), elapsedMillis,
                new GcProbe.Diff(0, 0, Map.of()),
                HeapProbe.snapshot(), HeapProbe.snapshot());
    }
}
