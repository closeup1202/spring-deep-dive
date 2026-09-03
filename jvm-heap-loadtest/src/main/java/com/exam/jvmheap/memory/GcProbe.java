package com.exam.jvmheap.memory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GC 누적 통계 스냅샷.
 *
 * <p>GC 는 "몇 번 돌았나(count)" 보다 "얼마나 멈췄나(time)" 가 중요하다.
 * 부하 테스트의 p99 가 튀는 이유는 대부분 여기에 있다.
 *
 * <p>주의: {@code getCollectionTime()} 은 누적 밀리초다. 절대값은 의미가 없고,
 * 반드시 두 스냅샷의 차이로 봐야 한다.
 */
public final class GcProbe {

    private GcProbe() {
    }

    public static Snapshot snapshot() {
        Map<String, Collector> collectors = new LinkedHashMap<>();
        long totalCount = 0;
        long totalMillis = 0;

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = Math.max(gc.getCollectionCount(), 0);
            long millis = Math.max(gc.getCollectionTime(), 0);
            collectors.put(gc.getName(), new Collector(count, millis));
            totalCount += count;
            totalMillis += millis;
        }
        return new Snapshot(totalCount, totalMillis, collectors);
    }

    public record Collector(long count, long millis) {
    }

    public record Snapshot(long totalCount, long totalMillis, Map<String, Collector> collectors) {

        public Diff since(Snapshot before) {
            Map<String, Collector> delta = new LinkedHashMap<>();
            collectors.forEach((name, now) -> {
                Collector then = before.collectors().getOrDefault(name, new Collector(0, 0));
                delta.put(name, new Collector(now.count() - then.count(), now.millis() - then.millis()));
            });
            return new Diff(totalCount - before.totalCount(), totalMillis - before.totalMillis(), delta);
        }
    }

    public record Diff(long collections, long pauseMillis, Map<String, Collector> byCollector) {

        /** 측정 구간 대비 GC 로 멈춰 있던 시간의 비율. 5% 를 넘으면 튜닝 대상이다. */
        public double pauseRatio(long elapsedMillis) {
            return elapsedMillis <= 0 ? 0 : (double) pauseMillis / elapsedMillis;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder("gc collections=%d pause=%dms".formatted(collections, pauseMillis));
            byCollector.forEach((name, c) -> {
                if (c.count() > 0) {
                    sb.append(" | ").append(name).append("=").append(c.count()).append("회/").append(c.millis()).append("ms");
                }
            });
            return sb.toString();
        }
    }
}
