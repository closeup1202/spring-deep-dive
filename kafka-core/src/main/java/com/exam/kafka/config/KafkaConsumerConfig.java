package com.exam.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.exam.kafka.domain");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.exam.kafka.domain.OrderEvent");

        // ─── 클라이언트 식별 ────────────────────────────────────────────────
        // 브로커 로그 / Kafka UI에서 어느 인스턴스인지 구별 가능
        // K8s 환경에서는 Pod명을 suffix로 추가 권장: "order-service-consumer-" + podName
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, "order-service-consumer");

        // ─── 오프셋 커밋 전략 ──────────────────────────────────────────────
        // false: 수동 커밋 (MANUAL_IMMEDIATE)
        //   → 처리 성공 후 ack.acknowledge() 호출 시 커밋
        //   → 처리 실패 시 커밋 안 됨 → 재시작 시 해당 오프셋부터 재처리
        // true(기본값): auto.commit.interval.ms마다 자동 커밋
        //   → 처리 중 장애 시 이미 커밋된 오프셋 재처리 불가 → 메시지 유실 가능
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // ─── 오프셋 초기화 전략 ────────────────────────────────────────────
        // earliest: 파티션 첫 번째 오프셋부터 (새 컨슈머 그룹이 처음 구독 시)
        // latest: 구독 시점 이후의 메시지만 (기본값)
        // none: 저장된 오프셋 없으면 예외 발생
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // ─── 리밸런싱 전략 ─────────────────────────────────────────────────
        // RangeAssignor (기본): 파티션을 컨슈머에 범위로 할당, 리밸런싱 시 전체 중단
        // CooperativeStickyAssignor: 점진적 리밸런싱
        //   → 이동이 필요한 파티션만 재할당 (나머지는 계속 소비)
        //   → "stop-the-world" 없음 → 처리 지연 최소화
        config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());

        // ─── 폴링 설정 ─────────────────────────────────────────────────────
        // max.poll.records: poll() 1번에 가져올 최대 레코드 수 (기본 500)
        // max.poll.interval.ms: poll() 호출 간 최대 허용 간격
        //   → 이 시간 내 poll()이 없으면 컨슈머 죽었다고 판단 → 리밸런싱 발생
        //   → 무거운 처리(외부 API, DB batch) 시 이 값을 처리 시간보다 크게 설정
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000); // 5분

        // ─── 하트비트 설정 ─────────────────────────────────────────────────
        // HeartBeat Thread는 poll() 호출과 독립적으로 동작
        // session.timeout.ms: 이 시간 내 heartbeat 없으면 컨슈머 장애로 판단
        // heartbeat.interval.ms: heartbeat 전송 주기 (session.timeout의 1/3 이하 권장)
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45_000);      // 45초
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 15_000);   // 15초

        // ─── 트랜잭션 격리 수준 ─────────────────────────────────────────────
        // read_committed: 커밋된 메시지만 소비 (트랜잭셔널 프로듀서 사용 시)
        // read_uncommitted(기본): 커밋되지 않은 메시지도 소비
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // ─── Fetch 최적화 ───────────────────────────────────────────────────
        // fetch.min.bytes: 브로커가 이 크기 이상 데이터가 모여야 응답 (기본값: 1byte)
        //   → 1byte 기본값은 1건만 있어도 즉시 응답 → 저처리량 환경에서 잦은 소규모 fetch
        //   → 1KB 이상으로 설정하면 한 번에 더 많이 가져와 네트워크 왕복 감소
        //   → 단, fetch.max.wait.ms 내에 이 크기 미달이면 그냥 응답하므로 지연 증가 없음
        // fetch.max.wait.ms: fetch.min.bytes 미충족 시 브로커가 최대 대기 후 응답 (기본값: 500ms)
        //   → fetch.min.bytes 높일수록 이 값도 함께 조정 (기본 500ms면 대부분 충분)
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1_024);  // 1KB (기본: 1byte)
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);  // 기본값과 동일

        // ─── Static Group Membership ────────────────────────────────────────
        // group.instance.id: 컨슈머에 고정 ID 부여 (기본값: 매 시작마다 새 멤버로 인식)
        //   → 재시작 시 같은 ID로 재접속하면 session.timeout.ms 내라면 리밸런싱 없이
        //     기존 파티션 그대로 유지
        //   → 유지보수·롤링 배포가 잦은 환경에서 불필요한 리밸런싱 방지
        //   → K8s: Pod명을 사용 (각 Pod마다 고유해야 함)
        // config.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, "order-consumer-0");

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        ConsumerFactory<String, Object> consumerFactory,
        KafkaTemplate<String, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // ─── AckMode: Spring Kafka 커밋 전략 ───────────────────────────────
        // 기본값: BATCH  → poll() 한 배치 전체 처리 후 자동 커밋
        //   단점: 배치 중간 실패 시 앞서 성공한 레코드도 재처리됨
        //
        // RECORD        → 각 레코드 리스너 완료 후 자동 커밋
        //   Acknowledgment 파라미터 불필요, 단순 구현에 적합
        //   단점: 처리 전에 커밋되는 타이밍이 없어 중복 처리 가능성 존재
        //
        // MANUAL_IMMEDIATE → ack.acknowledge() 호출 즉시 커밋 (현재 설정)
        //   처리 성공 여부를 직접 제어 → at-least-once 보장
        //   단점: Acknowledgment 파라미터를 리스너마다 선언해야 함
        //
        // MANUAL        → ack.acknowledge() 호출 후 다음 poll() 직전에 커밋
        //   MANUAL_IMMEDIATE보다 커밋 시점이 느슨 (I/O 횟수 약간 감소)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ─── Poll 타임아웃 ──────────────────────────────────────────────────
        // poll() 시 브로커로부터 응답을 기다리는 최대 시간 (기본값: 5000ms)
        // 짧게 설정할수록 SIGTERM 수신 후 종료 응답성 향상
        factory.getContainerProperties().setPollTimeout(3_000);

        // ─── Graceful Shutdown ──────────────────────────────────────────────
        // SIGTERM 수신 후 현재 처리 중인 레코드 완료를 기다리는 최대 시간 (기본값: 10,000ms)
        // K8s 환경: terminationGracePeriodSeconds(기본 30s)보다 짧게 설정
        factory.getContainerProperties().setShutdownTimeout(30_000);

        // 컨슈머 스레드 수: 파티션 수 이하로 설정 (초과해도 의미 없음)
        factory.setConcurrency(3);

        // 에러 핸들러: 지수 백오프 재시도 + 최종 실패 시 DLT 발행
        factory.setCommonErrorHandler(defaultErrorHandler(kafkaTemplate));

        return factory;
    }

    /**
     * DefaultErrorHandler: @KafkaListener 레벨 에러 핸들링.
     * <p>
     * 동작:
     *   1. 처리 실패 → ExponentialBackOff에 따라 재시도 (컨슈머 스레드 내 블로킹)
     *   2. 재시도 소진 → DeadLetterPublishingRecoverer가 DLT로 발행
     * <p>
     * 블로킹 재시도 주의:
     *   재시도 대기 중 poll()이 호출되지 않음
     *   → max.poll.interval.ms 초과 주의
     *   → 긴 지연이 필요하면 @RetryableTopic(논블로킹) 사용 권장
     */
    private DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            // DLT 토픽 선택 로직: 원본 토픽명 + ".DLT", 같은 파티션 번호 유지
            (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        // 지수 백오프: 1s → 2s → 4s → 8s → 16s (최대 5회 재시도)
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxElapsedTime(31_000); // 1+2+4+8+16=31초 이내

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 재시도하지 않을 예외: 비즈니스 오류는 재시도해도 의미 없음
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            IllegalStateException.class
        );

        // 재시도 시 로그 출력
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
           log.warn("[ErrorHandler] 재시도 {}/5: topic={}, offset={}, cause={}",
                    deliveryAttempt, record.topic(), record.offset(), ex.getMessage())
        );

        return errorHandler;
    }
}
