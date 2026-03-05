package com.exam.kafka.config;

import com.exam.kafka.domain.OrderEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false); // 헤더에 타입 정보 미포함 (수신자 의존성 제거)

        // ─── 클라이언트 식별 ────────────────────────────────────────────────
        // 브로커 로그 / Kafka UI에서 어느 인스턴스의 프로듀서인지 구별 가능
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "order-service-producer");

        // ─── 멱등성(Idempotence) 설정 ──────────────────────────────────────
        // Kafka 3.0+에서는 기본값이 true이지만 명시적 설정 권장
        // 동작 원리: Producer에 PID 부여 + 각 메시지에 시퀀스 번호 부여
        //   → 브로커가 중복 메시지를 시퀀스 번호로 감지하여 드롭
        //   → 네트워크 오류로 재전송해도 중복 저장 방지
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");                       // ISR 전체 확인 (멱등성 필수 설정)
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);        // 멱등성 사용 시 무한 재시도
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // 멱등성: 최대 5까지 허용

        // ─── 배치 / 처리량 설정 ────────────────────────────────────────────
        // batch.size: 배치 최대 크기 (이 크기 채워지면 즉시 전송)
        // linger.ms: 배치가 안 채워져도 이 시간 후에는 전송
        // → 둘 다 설정 시: 먼저 도달하는 조건에서 전송
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384);      // 16KB
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);            // 5ms 대기 (처리량 vs 지연 트레이드오프)
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33_554_432L); // Record Accumulator 32MB

        // ─── 압축 설정 ─────────────────────────────────────────────────────
        // snappy: CPU 오버헤드 낮음, 압축률 중간 (일반적 권장)
        // lz4: snappy보다 빠름 (고처리량 환경)
        // gzip: 압축률 높음, CPU 오버헤드 큼 (저장 비용 절감 중요 시)
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // ─── 타임아웃 설정 ─────────────────────────────────────────────────
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);    // 브로커 응답 대기 30초
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);  // 전체 전송 타임아웃 2분

        // ─── 버퍼 백프레셔 ─────────────────────────────────────────────────
        // max.block.ms: Record Accumulator가 가득 찼거나 메타데이터 조회 실패 시
        //   send() 호출 스레드가 블로킹되는 최대 시간 (기본값: 60,000ms)
        //   → 기본값 60초: 브로커 장애 시 호출 스레드가 1분간 묶임 → API 응답 지연
        //   → 5초로 단축하면 빠른 실패(fail-fast) → TimeoutException 즉시 수신
        //   → 호출자에서 Outbox 패턴 등으로 재발행 처리 가능
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
