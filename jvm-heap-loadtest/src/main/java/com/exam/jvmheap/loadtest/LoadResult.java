package com.exam.jvmheap.loadtest;

import com.exam.jvmheap.memory.Bytes;
import com.exam.jvmheap.memory.GcProbe;
import com.exam.jvmheap.memory.HeapProbe;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * 부하 테스트 결과.
 *
 * <p>평균 응답시간은 거의 쓸모가 없다. 평균은 느린 요청을 숨긴다.
 * 실무에서 SLO 로 쓰는 값은 p95, p99 다. 사용자 100명 중 1명이 겪는 시간이
 * 그 서비스의 체감 품질을 결정하기 때문이다.
 *
 * <p>여기에 GC 정보를 같이 담는 이유: p99 가 튀는 원인의 상당수가 GC pause 다.
 * 두 숫자를 따로 보면 절대 연결되지 않는다.
 */
public record LoadResult(
        String name,
        long[] latenciesMicros,
        long errors,
        Map<Integer, Long> statusCounts,
        long elapsedMillis,
        GcProbe.Diff gc,
        HeapProbe.Snapshot heapBefore,
        HeapProbe.Snapshot heapAfter
) {

    public long total() {
        return latenciesMicros.length + errors;
    }

    public double tps() {
        return elapsedMillis == 0 ? 0 : latenciesMicros.length * 1000.0 / elapsedMillis;
    }

    public double errorRate() {
        return total() == 0 ? 0 : (double) errors / total();
    }

    /** 정렬된 배열에서 백분위수를 뽑는다. 호출 전에 정렬돼 있어야 한다. */
    public double percentileMillis(double percentile) {
        if (latenciesMicros.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * latenciesMicros.length) - 1;
        index = Math.max(0, Math.min(index, latenciesMicros.length - 1));
        return latenciesMicros[index] / 1000.0;
    }

    public double meanMillis() {
        return latenciesMicros.length == 0 ? 0
                : Arrays.stream(latenciesMicros).average().orElse(0) / 1000.0;
    }

    public double maxMillis() {
        return latenciesMicros.length == 0 ? 0
                : latenciesMicros[latenciesMicros.length - 1] / 1000.0;
    }

    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n================ load result: ").append(name).append(" ================\n");
        sb.append("duration      : %d ms%n".formatted(elapsedMillis));
        sb.append("requests      : %d (ok=%d, error=%d, errorRate=%.2f%%)%n"
                .formatted(total(), latenciesMicros.length, errors, errorRate() * 100));
        sb.append("throughput    : %.1f req/s%n".formatted(tps()));
        sb.append("latency(ms)   : mean=%.1f p50=%.1f p90=%.1f p95=%.1f p99=%.1f max=%.1f%n"
                .formatted(meanMillis(), percentileMillis(50), percentileMillis(90),
                        percentileMillis(95), percentileMillis(99), maxMillis()));
        sb.append("status        : ").append(new TreeMap<>(statusCounts)).append("\n");
        sb.append("gc            : %s (구간의 %.2f%%)%n"
                .formatted(gc.summary(), gc.pauseRatio(elapsedMillis) * 100));
        sb.append("heap used     : %s -> %s (delta %s)%n".formatted(
                Bytes.human(heapBefore.used()),
                Bytes.human(heapAfter.used()),
                Bytes.human(heapAfter.usedDeltaFrom(heapBefore))));
        sb.append("heap max      : %s (사용률 %.1f%%)%n"
                .formatted(Bytes.human(heapAfter.max()), heapAfter.usedRatioOfMax() * 100));
        sb.append("=========================================================\n");
        return sb.toString();
    }
}
