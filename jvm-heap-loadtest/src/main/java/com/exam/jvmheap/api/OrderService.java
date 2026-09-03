package com.exam.jvmheap.api;

import com.exam.jvmheap.memory.BoundedLruCache;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 부하 테스트의 "정상 워크로드" 역할.
 *
 * <p>요청마다 짧게 살다 죽는 객체를 만든다. 이런 객체는 Young 영역에서 태어나
 * 대부분 첫 Minor GC 때 사라진다(= weak generational hypothesis).
 * 그래서 이 엔드포인트에 아무리 부하를 넣어도 Old 는 잘 차지 않는다.
 * 힙 그래프가 톱니 모양으로 오르내리기만 하면 건강한 상태다.
 */
@Service
public class OrderService {

    /** 상한이 있는 캐시. 오래 사는 객체지만 개수가 고정이라 Old 가 무한히 늘지 않는다. */
    private final BoundedLruCache<String, Order> recentOrders = new BoundedLruCache<>(500);
    private final AtomicLong counter = new AtomicLong();

    public Order place(int itemCount) {
        String id = UUID.randomUUID().toString();

        // 요청 스코프 임시 객체들. 메서드가 끝나면 곧바로 쓰레기가 된다.
        StringBuilder receipt = new StringBuilder(256);
        long amount = 0;
        for (int i = 0; i < itemCount; i++) {
            long price = ThreadLocalRandom.current().nextLong(1000, 50000);
            amount += price;
            receipt.append("item-").append(i).append(":").append(price).append("\n");
        }

        Order order = new Order(id, counter.incrementAndGet(), itemCount, amount, receipt.toString());
        recentOrders.put(id, order);
        return order;
    }

    public Order find(String id) {
        return recentOrders.get(id);
    }

    public int cachedCount() {
        return recentOrders.size();
    }

    public record Order(String id, long sequence, int itemCount, long amount, String receipt) {
    }
}
