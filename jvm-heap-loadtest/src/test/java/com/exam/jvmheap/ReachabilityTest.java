package com.exam.jvmheap;

import com.exam.jvmheap.memory.LeakySessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "GC 는 무엇을 수거하는가" 를 코드로 확인한다.
 *
 * <p>핵심: GC 는 쓰지 않는 객체가 아니라 <b>도달할 수 없는 객체</b>를 수거한다.
 * 이 한 문장이 메모리 누수의 정의 전체다.
 * 다시는 쓰지 않을 객체라도 GC Root 에서 참조 사슬이 이어져 있으면 영원히 남는다.
 *
 * <p>System.gc() 는 권고일 뿐 실행이 보장되지 않는다. 테스트에서는 반복해서 확인하고,
 * 운영 코드에서는 절대 호출하지 않는다 (Full GC 를 직접 유발하는 꼴이다).
 */
class ReachabilityTest {

    @Test
    @DisplayName("싱글턴이 붙잡고 있으면 GC 를 아무리 돌려도 수거되지 않는다")
    void strongReferenceSurvivesGc() {
        LeakySessionStore store = new LeakySessionStore();
        String key = store.put(1024);

        // 감시자만 남기고 강한 참조는 남기지 않는다 (헬퍼 메서드 프레임이 사라지도록)
        WeakReference<Object> watcher = watch(store, key);

        forceGc();

        assertThat(watcher.get())
                .as("store 가 참조 사슬을 유지하므로 살아있다 - 이것이 누수의 메커니즘")
                .isNotNull();

        store.clear();

        assertThat(waitForCollection(watcher))
                .as("참조를 끊어야 비로소 수거 대상이 된다")
                .isTrue();
    }

    @Test
    @DisplayName("WeakReference 는 강한 참조가 사라지는 즉시 수거 대상이 된다")
    void weakReferenceIsClearedWhenUnreachable() {
        ReferenceQueue<byte[]> queue = new ReferenceQueue<>();
        WeakReference<byte[]> weak = watchNewArray(queue);

        assertThat(waitForCollection(weak)).isTrue();

        Reference<? extends byte[]> polled = queue.poll();
        assertThat(polled)
                .as("ReferenceQueue 로 수거 시점을 통지받는다 - 캐시 정리 훅의 원리")
                .isSameAs(weak);
    }

    @Test
    @DisplayName("SoftReference 는 힙이 부족해질 때까지 살아남는다")
    void softReferenceSurvivesNormalGc() {
        SoftReference<byte[]> soft = new SoftReference<>(new byte[1024 * 1024]);

        forceGc();

        // 규약상 OOM 직전까지 유지된다. 즉 "힙이 꽉 찰 때까지 캐시가 자란다".
        // SoftReference 캐시가 GC 압력을 키우는 안티패턴으로 불리는 이유다.
        assertThat(soft.get())
                .as("일반 GC 로는 SoftReference 가 정리되지 않는다")
                .isNotNull();
    }

    @Test
    @DisplayName("HashMap 은 키를 붙잡지만 WeakHashMap 은 놓아준다")
    void weakHashMapReleasesKeys() {
        Map<Object, byte[]> strongMap = new HashMap<>();
        Map<Object, byte[]> weakMap = new WeakHashMap<>();

        WeakReference<Object> strongKeyWatcher = putAndWatch(strongMap);
        putOnly(weakMap);

        forceGc();

        // HashMap 은 키를 강하게 참조한다 -> 키도 값도 살아있다
        assertThat(strongKeyWatcher.get()).isNotNull();
        assertThat(strongMap).hasSize(1);

        // WeakHashMap 은 키가 unreachable 이 되면 엔트리를 버린다.
        // 함정: 값이 키를 참조하면(예: value 안에 key 를 담으면) 이 효과는 사라진다.
        assertThat(sizeAfterGc(weakMap))
                .as("WeakHashMap 은 키가 수거되면 엔트리가 사라진다")
                .isZero();
    }

    private WeakReference<Object> watch(LeakySessionStore store, String key) {
        return new WeakReference<>(store.peek(key));
    }

    private WeakReference<byte[]> watchNewArray(ReferenceQueue<byte[]> queue) {
        return new WeakReference<>(new byte[1024 * 1024], queue);
    }

    private WeakReference<Object> putAndWatch(Map<Object, byte[]> map) {
        Object key = new Object();
        map.put(key, new byte[512 * 1024]);
        return new WeakReference<>(key);
    }

    private void putOnly(Map<Object, byte[]> map) {
        map.put(new Object(), new byte[512 * 1024]);
    }

    private int sizeAfterGc(Map<Object, byte[]> weakMap) {
        for (int i = 0; i < 20; i++) {
            if (weakMap.isEmpty()) {
                return 0;
            }
            forceGc();
        }
        return weakMap.size();
    }

    private void forceGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            sleep(50);
        }
    }

    private boolean waitForCollection(Reference<?> reference) {
        for (int i = 0; i < 20; i++) {
            if (reference.get() == null) {
                return true;
            }
            System.gc();
            sleep(50);
        }
        return reference.get() == null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
