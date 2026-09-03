package com.exam.jvmheap;

import com.exam.jvmheap.memory.BoundedLruCache;
import com.exam.jvmheap.memory.Bytes;
import com.exam.jvmheap.memory.HeapProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캐시는 상한이 있어야 캐시고, 없으면 이름만 캐시인 누수다.
 *
 * <p>실무에서 가장 흔한 OOM 원인 1순위가 이것이다.
 * "조회가 느려서 Map 에 담아뒀다" 로 시작해 만료 정책 없이 배포된다.
 * 트래픽이 적을 때는 아무 일도 없다가, 캠페인 하루에 터진다.
 */
class CacheBoundaryTest {

    @Test
    @DisplayName("상한 없는 Map 은 넣는 만큼 힙을 먹는다")
    void unboundedMapGrowsWithoutLimit() {
        HeapProbe.Snapshot before = afterGc();

        Map<String, byte[]> cache = new HashMap<>();
        for (int i = 0; i < 3000; i++) {
            cache.put("key-" + i, new byte[10 * 1024]);    // 10KB x 3000 = 약 30MB
        }

        HeapProbe.Snapshot after = afterGc();
        System.out.printf("[unbounded] entries=%d, 힙 증가=%s%n",
                cache.size(), Bytes.human(after.usedDeltaFrom(before)));

        assertThat(cache).hasSize(3000);
        assertThat(after.usedDeltaFrom(before)).isGreaterThan(Bytes.mb(20));
    }

    @Test
    @DisplayName("상한이 있는 LRU 는 아무리 넣어도 메모리가 평평하다")
    void boundedCacheStaysFlat() {
        HeapProbe.Snapshot before = afterGc();

        BoundedLruCache<String, byte[]> cache = new BoundedLruCache<>(100);
        for (int i = 0; i < 3000; i++) {
            cache.put("key-" + i, new byte[10 * 1024]);
        }

        HeapProbe.Snapshot after = afterGc();
        System.out.printf("[bounded] entries=%d/%d, 힙 증가=%s%n",
                cache.size(), cache.maxEntries(), Bytes.human(after.usedDeltaFrom(before)));

        assertThat(cache.size()).isEqualTo(100);
        assertThat(after.usedDeltaFrom(before))
                .as("100개 x 10KB = 1MB 수준을 넘지 않는다")
                .isLessThan(Bytes.mb(10));
    }

    @Test
    @DisplayName("LRU 는 오래된 것이 아니라 오래 안 쓴 것을 버린다")
    void lruEvictsLeastRecentlyUsed() {
        BoundedLruCache<String, String> cache = new BoundedLruCache<>(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        cache.get("a");            // a 를 최근 사용으로 끌어올린다
        cache.put("d", "4");       // 상한 초과 -> 가장 오래 안 쓴 b 가 나간다

        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.get("b")).as("가장 오래 안 쓴 항목이 축출된다").isNull();
        assertThat(cache.get("c")).isEqualTo("3");
        assertThat(cache.get("d")).isEqualTo("4");
        assertThat(cache.size()).isEqualTo(3);
    }

    private HeapProbe.Snapshot afterGc() {
        HeapProbe.suggestGcForTest();
        HeapProbe.suggestGcForTest();
        return HeapProbe.snapshot();
    }
}
