package com.exam.jvmheap;

import com.exam.jvmheap.memory.Bytes;
import com.exam.jvmheap.memory.GcProbe;
import com.exam.jvmheap.memory.HeapProbe;
import com.exam.jvmheap.memory.LeakySessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "할당이 많은 것" 과 "누수" 를 숫자로 구분한다.
 *
 * <p>운영 중 힙 그래프를 보고 판단해야 하는 딱 하나의 질문:
 * <b>GC 직후의 저점(baseline)이 계속 올라가고 있는가?</b>
 * 저점이 평평하면 아무리 톱니가 크고 GC 가 잦아도 누수가 아니다.
 * 저점이 우상향하면 그것이 누수다.
 */
class HeapGrowthTest {

    @Test
    @DisplayName("버려지는 객체는 아무리 많이 만들어도 힙에 남지 않는다")
    void garbageDoesNotAccumulate() {
        HeapProbe.Snapshot before = afterGc();
        GcProbe.Snapshot gcBefore = GcProbe.snapshot();

        // 최대 힙의 2배를 쓰레기로 만든다. 반드시 GC 가 여러 번 돈다.
        long churn = Math.min(before.max() * 2, Bytes.mb(2048));
        long allocated = 0;
        byte[] sink = new byte[8];
        while (allocated < churn) {
            sink = new byte[16 * 1024];
            sink[0] = 1;
            allocated += sink.length;
        }
        assertThat(sink).isNotNull();

        GcProbe.Diff gc = GcProbe.snapshot().since(gcBefore);
        HeapProbe.Snapshot after = afterGc();
        long retained = after.usedDeltaFrom(before);

        System.out.printf("[garbage] 할당=%s, %s, 힙 잔존=%s%n",
                Bytes.human(allocated), gc.summary(), Bytes.human(retained));

        assertThat(gc.collections())
                .as("힙 크기를 넘는 할당을 했으므로 GC 는 반드시 돌았다")
                .isGreaterThan(0);
        assertThat(retained)
                .as("할당량의 5%% 미만만 남는다 - 이것은 누수가 아니다")
                .isLessThan(allocated / 20);
    }

    @Test
    @DisplayName("참조가 남는 객체는 GC 를 돌려도 힙에서 내려오지 않는다")
    void retainedObjectsAccumulate() {
        LeakySessionStore store = new LeakySessionStore();
        HeapProbe.Snapshot before = afterGc();

        for (int i = 0; i < 200; i++) {
            store.put(100);                          // 100KB x 200 = 약 20MB
        }

        HeapProbe.Snapshot afterLeak = afterGc();
        long leaked = afterLeak.usedDeltaFrom(before);
        System.out.printf("[leak] 보관=%s, 힙 증가=%s%n",
                Bytes.human(store.approximateBytes()), Bytes.human(leaked));

        assertThat(leaked)
                .as("GC 이후에도 힙이 내려오지 않는다 = 누수")
                .isGreaterThan(Bytes.mb(10));

        // 참조를 끊으면 회수된다. 실제 장애 대응에서 "재시작하면 낫는" 이유이기도 하다.
        store.clear();
        HeapProbe.Snapshot afterClear = afterGc();
        System.out.printf("[leak] clear 후 힙=%s%n", Bytes.human(afterClear.used()));

        assertThat(afterClear.used())
                .as("참조를 끊으면 회수된다")
                .isLessThan(afterLeak.used());
    }

    @Test
    @DisplayName("힙 풀 이름으로 사용 중인 GC 를 알 수 있다")
    void heapPoolsRevealTheCollector() {
        HeapProbe.Snapshot snapshot = HeapProbe.snapshot();

        System.out.println("[heap] " + snapshot.summary());
        snapshot.pools().forEach((name, usage) ->
                System.out.printf("  - %-24s used=%s%n", name, Bytes.human(usage.used())));
        System.out.println("[gc] " + GcProbe.snapshot().collectors());

        // G1: G1 Eden Space / G1 Survivor Space / G1 Old Gen
        // Parallel: PS Eden Space / PS Survivor Space / PS Old Gen
        // ZGC: ZHeap
        assertThat(snapshot.pools()).isNotEmpty();
        assertThat(snapshot.max()).isPositive();
    }

    @Test
    @DisplayName("동시 요청 수 x 요청당 메모리 = 상주 메모리 (부하가 힙을 밀어올리는 경로)")
    void concurrencyMultipliesPerRequestMemory() {
        int concurrency = 50;
        int perRequestKb = 512;

        HeapProbe.Snapshot before = afterGc();
        List<byte[]> inFlight = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            inFlight.add(new byte[perRequestKb * 1024]);   // 처리 중인 요청이 붙잡고 있는 버퍼
        }
        HeapProbe.Snapshot during = HeapProbe.snapshot();

        long expected = (long) concurrency * perRequestKb * 1024;
        long actual = during.usedDeltaFrom(before);
        System.out.printf("[concurrency] 동시 %d건 x %dKB = 예상 %s, 실제 증가 %s%n",
                concurrency, perRequestKb, Bytes.human(expected), Bytes.human(actual));

        assertThat(actual).isGreaterThan(expected / 2);
        assertThat(inFlight).hasSize(concurrency);

        // 시사점: TPS 를 2배로 올리면 상주 메모리도 2배가 된다.
        // 부하 테스트 없이 -Xmx 를 정하는 것이 위험한 이유가 이것이다.
    }

    private HeapProbe.Snapshot afterGc() {
        HeapProbe.suggestGcForTest();
        HeapProbe.suggestGcForTest();
        return HeapProbe.snapshot();
    }
}
