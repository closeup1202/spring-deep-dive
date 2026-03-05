package com.exam.redis.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisStockCacheRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "product:stock:";

    // ─── Lua Script — Atomic 재고 감소 ──────────────────────────────────────
    //
    // GET과 DECRBY를 하나의 원자 연산으로 묶어 Race Condition 제거.
    //
    // Redis 단일 스레드 + Lua Script 원자 실행
    //   → [GET → 검증 → DECRBY] 전체가 하나의 명령으로 처리
    //   → 명령들 사이에 다른 클라이언트 요청이 끼어들 수 없음
    //
    // 반환값:
    //   >= 0 : 감소 성공 (반환값 = 감소 후 남은 재고)
    //    -1  : 재고 부족
    //    -2  : 키 없음 (재고 미초기화)
    private static final RedisScript<Long> DECREASE_STOCK_SCRIPT = RedisScript.of(
        """
        local stock = tonumber(redis.call('GET', KEYS[1]))
        if stock == nil then
            return -2
        end
        if stock < tonumber(ARGV[1]) then
            return -1
        end
        return redis.call('DECRBY', KEYS[1], ARGV[1])
        """,
        Long.class
    );

    /**
     * 재고 초기화.
     */
    public void init(Long productId, int quantity, long ttlSeconds) {
        String key = KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(quantity), ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 재고 조회.
     */
    public Long getStock(Long productId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + productId);
        return value == null ? null : Long.parseLong(value);
    }

    /**
     * Lua Script 기반 Atomic 재고 감소.
     *
     * @return true: 감소 성공 / false: 재고 부족 또는 키 없음
     */
    public boolean decrease(Long productId, int quantity) {
        String key = KEY_PREFIX + productId;

        Long result = redisTemplate.execute(
            DECREASE_STOCK_SCRIPT,
            List.of(key),
            String.valueOf(quantity)
        );

        // -2: 키 없음, -1: 재고 부족, 0 이상: 감소 성공
        return result != null && result >= 0;
    }

    // ─── [Race Condition 재현] DECR 방식 — 실무에서 사용하지 말 것 ───────────
    //
    // GET 없이 DECR 후 음수 체크 + INCR 복구 방식의 문제:
    //   DECR과 복구 INCR이 두 개의 독립 명령 → 사이에 다른 요청이 끼어들 수 있음
    //
    // 재고 1, 스레드 2개 동시 요청 시나리오:
    //   Thread-A: DECR → stock = 0  (0 >= 0 이므로 성공 판단, 주문 처리 시작)
    //   Thread-B: DECR → stock = -1 (음수 → INCR 복구 실행)
    //   (이때 Thread-A는 이미 성공 반환 → 재고 차감 완료 상태)
    //   Thread-B INCR 실행 → 재고 0 → 1 로 복구됨
    //   → 결과: 재고 1인데 Thread-A 주문 성공 + 재고가 1로 복구되어 다음 요청도 성공 가능
    //
    // 더 심각한 시나리오 (재고 2, 스레드 3개):
    //   Thread-A: DECR → 1  (성공)
    //   Thread-B: DECR → 0  (성공)
    //   Thread-C: DECR → -1 → INCR 복구
    //   → 여기까지는 정상처럼 보이지만
    //   Thread-C의 INCR이 네트워크 지연으로 늦게 도착하면:
    //   재고가 일시적으로 -1 → 0 복구 → 다음 요청이 DECR → -1 로 진행 가능
    //   → 초과 판매 발생
    /**
     * @deprecated Race Condition 발생 가능. decrease() 사용할 것.
     */
    @Deprecated
    public boolean decreaseUnsafe(Long productId, int quantity) {
        String key = KEY_PREFIX + productId;

        Long stock = redisTemplate.opsForValue().decrement(key, quantity);
        if (stock == null || stock < 0) {
            redisTemplate.opsForValue().increment(key, quantity); // 복구 INCR
            return false;
        }
        return true;
    }
}
