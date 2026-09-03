package com.exam.jvmheap.memory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 힙 사용량 스냅샷.
 *
 * <p>{@code Runtime.getRuntime().freeMemory()} 대신 {@link java.lang.management.MemoryMXBean} 을 쓴다.
 * Runtime 은 "지금 커밋된 힙 중 남은 양"만 알려주고, 세대별 분해가 안 된다.
 * 실무에서 필요한 건 대부분 "Old 영역이 차오르고 있는가" 이고, 그건 풀 단위로 봐야 한다.
 */
public final class HeapProbe {

    private HeapProbe() {
    }

    public static Snapshot snapshot() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        Map<String, PoolUsage> pools = new LinkedHashMap<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            MemoryUsage usage = pool.getUsage();
            pools.put(pool.getName(), new PoolUsage(usage.getUsed(), usage.getCommitted(), usage.getMax()));
        }
        return new Snapshot(heap.getUsed(), heap.getCommitted(), heap.getMax(), pools);
    }

    /**
     * 테스트에서만 쓴다. System.gc() 는 "권고"라서 실행이 보장되지 않는다.
     * 운영 코드에서 호출하면 Full GC 를 유발해 STW 를 직접 만드는 꼴이 된다.
     */
    public static void suggestGcForTest() {
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record PoolUsage(long used, long committed, long max) {
        public String humanUsed() {
            return Bytes.human(used);
        }
    }

    public record Snapshot(long used, long committed, long max, Map<String, PoolUsage> pools) {

        /** 커밋된 힙 대비 사용률. GC 가 얼마나 자주 돌지를 좌우한다. */
        public double usedRatioOfCommitted() {
            return committed == 0 ? 0 : (double) used / committed;
        }

        /** -Xmx 대비 사용률. 이 값이 계속 90% 를 넘으면 OOM 이 임박한 것이다. */
        public double usedRatioOfMax() {
            return max <= 0 ? 0 : (double) used / max;
        }

        public long usedDeltaFrom(Snapshot before) {
            return used - before.used;
        }

        public String summary() {
            return "heap used=%s committed=%s max=%s (%.1f%% of max)"
                    .formatted(Bytes.human(used), Bytes.human(committed), Bytes.human(max), usedRatioOfMax() * 100);
        }
    }
}
