package com.exam.jvmheap.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 상한이 있는 LRU 캐시.
 *
 * <p>{@link LinkedHashMap} 의 accessOrder 생성자 + {@code removeEldestEntry} 오버라이드는
 * 라이브러리 없이 쓸 수 있는 가장 짧은 LRU 구현이다.
 *
 * <p>실무에서는 Caffeine 을 쓴다. 여기서 직접 구현하는 이유는
 * "캐시는 상한이 있어야 캐시고, 없으면 그냥 누수다" 를 코드로 보기 위해서다.
 */
public class BoundedLruCache<K, V> {

    private final Map<K, V> map;
    private final int maxEntries;

    public BoundedLruCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.map = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedLruCache.this.maxEntries;
            }
        });
    }

    public void put(K key, V value) {
        map.put(key, value);
    }

    public V get(K key) {
        return map.get(key);
    }

    public int size() {
        return map.size();
    }

    public int maxEntries() {
        return maxEntries;
    }
}
