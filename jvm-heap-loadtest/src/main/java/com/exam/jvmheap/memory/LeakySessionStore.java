package com.exam.jvmheap.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 전형적인 메모리 누수 재현용 저장소.
 *
 * <p>실무에서 만나는 누수는 대부분 이 모양이다:
 * <ul>
 *   <li>싱글턴 빈(= GC Root 에서 도달 가능)이</li>
 *   <li>컬렉션을 들고 있는데</li>
 *   <li>넣기만 하고 빼는 코드가 없다</li>
 * </ul>
 *
 * <p>"조회 성능을 위해 캐싱했다" 로 시작해서 만료 정책 없이 방치되는 경우가 압도적으로 많다.
 * 누수의 본질은 "쓰지 않는 객체를 GC 가 수거하지 못하는 것" 이지, 할당량이 많은 것이 아니다.
 */
public class LeakySessionStore {

    /** static 이 아니어도 싱글턴 빈이 들고 있으면 결과는 같다. 도달 가능하면 살아남는다. */
    private final Map<String, List<byte[]>> sessions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** 세션 하나당 payloadKb 만큼의 바이트 배열을 붙여 저장한다. */
    public String put(int payloadKb) {
        String key = "session-" + sequence.incrementAndGet();
        List<byte[]> payload = new ArrayList<>(1);
        payload.add(new byte[payloadKb * 1024]);
        sessions.put(key, payload);
        return key;
    }

    /** 테스트에서 payload 의 도달 가능성을 관찰하기 위한 접근자. */
    public List<byte[]> peek(String key) {
        return sessions.get(key);
    }

    public int size() {
        return sessions.size();
    }

    public long approximateBytes() {
        return sessions.values().stream()
                .flatMap(List::stream)
                .mapToLong(b -> b.length)
                .sum();
    }

    /** 누수를 끊는 유일한 방법: 참조를 지운다. */
    public void clear() {
        sessions.clear();
    }
}
