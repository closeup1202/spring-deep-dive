# Spring Cache & Redis 실무 가이드

Spring의 캐시 추상화(`@Cacheable`)와 Redis를 활용하여 성능을 최적화하는 방법을 학습합니다.

## 1. 캐시 전략 (Caching Strategies)

### Look Aside (Lazy Loading)
*   **동작**: 데이터를 찾을 때 캐시를 먼저 확인하고, 없으면(Cache Miss) DB에서 가져와 캐시에 저장합니다.
*   **장점**: 캐시가 죽어도 DB에서 데이터를 가져올 수 있어 서비스 장애로 이어지지 않습니다.
*   **단점**: 캐시에 없는 데이터가 처음 조회될 때(Cold Start) DB 부하가 몰릴 수 있습니다.
*   **구현**: `@Cacheable` 어노테이션이 이 패턴을 자동으로 수행합니다.

### Write Back (Write Behind)
*   **동작**: 데이터를 캐시에만 먼저 쓰고, 나중에 배치 등으로 DB에 한꺼번에 씁니다.
*   **장점**: 쓰기 성능이 매우 빠릅니다.
*   **단점**: 캐시가 죽으면 데이터가 유실될 수 있습니다. (Spring Cache로는 구현이 복잡함)

## 2. 주요 어노테이션

### @Cacheable
*   `@Cacheable(cacheNames = "products", key = "#id")`
*   메서드 실행 전 캐시를 확인합니다. 데이터가 있으면 메서드를 실행하지 않고 캐시 값을 리턴합니다.

### @CacheEvict
*   `@CacheEvict(cacheNames = "products", key = "#id")`
*   데이터가 수정되거나 삭제될 때, 캐시된 데이터도 함께 삭제하여 **데이터 불일치(Inconsistency)**를 방지합니다.

### @CachePut
*   `@CachePut(cacheNames = "products", key = "#id")`
*   메서드 실행 결과를 무조건 캐시에 저장(덮어쓰기)합니다. 주로 수정 메서드에 사용하여 DB 조회 없이 캐시를 최신화할 때 씁니다.

## 3. 실무 설정 포인트 (`CacheConfig`)
1.  **직렬화(Serialization)**: 기본 JdkSerializationRedisSerializer는 사람이 읽을 수 없는 바이너리로 저장됩니다. `GenericJackson2JsonRedisSerializer`를 사용하여 JSON 형태로 저장하는 것이 디버깅에 좋습니다.
2.  **TTL(Time To Live)**: 캐시 데이터는 영원히 저장되면 안 됩니다. 비즈니스 특성에 맞게 만료 시간을 설정해야 합니다. (예: 상품 10분, 공지사항 1일)

## 4. 테스트 (`CacheTest`)
*   `@SpyBean`을 사용하여 `Repository`의 실제 호출 횟수를 카운팅함으로써 캐시가 제대로 동작하는지(DB 조회를 안 하는지) 검증할 수 있습니다.
