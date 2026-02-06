# Serialization Deep Dive: Jackson & Avro

## 1. 개요
MSA 환경에서 서비스 간 통신 시 데이터 직렬화 성능은 전체 시스템의 처리량(Throughput)에 큰 영향을 미칩니다.
가장 대중적인 **JSON(Jackson)**과 고성능 바이너리 포맷인 **Avro**를 비교하고 실습합니다.

## 2. Jackson (JSON) 심화
Spring Boot의 기본 JSON 라이브러리입니다.
### Custom Serializer (`@JsonSerialize`)
- 특정 필드(주민번호, 전화번호 등)를 변환할 때 나만의 로직을 적용할 수 있습니다.
- `JsonSerializer<T>`를 상속받아 구현합니다.
- 예: `010-1234-5678` -> `010-1234-****`

## 3. Apache Avro (Binary)
Hadoop, Kafka 등 대용량 데이터 처리에 주로 사용되는 바이너리 직렬화 프레임워크입니다.
### 특징
- **Schema 기반:** 데이터에 필드명("name", "age")을 포함하지 않고 값만 순서대로 저장하므로 크기가 매우 작습니다.
- **Schema Evolution:** 스키마가 변경되어도(필드 추가 등) 호환성을 유지할 수 있는 강력한 기능을 제공합니다.

## 4. 실습 내용
`src/test/java/com/exam/serial/SerializationTest.java`를 실행하세요.

1. **`jacksonMaskingTest`**: 전화번호 뒷자리가 `****`로 변환되는지 확인.
2. **`avroSizeTest`**: 동일한 데이터를 직렬화했을 때, Avro가 JSON보다 용량이 훨씬 작은지 확인. (보통 2배 이상 차이남)
