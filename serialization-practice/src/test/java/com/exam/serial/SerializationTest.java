package com.exam.serial;

import com.exam.serial.dto.User;
import com.exam.serial.service.AvroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AvroService avroService;

    @Test
    @DisplayName("Jackson: 커스텀 Serializer로 전화번호가 마스킹되어야 한다")
    void jacksonMaskingTest() throws Exception {
        // Given
        User user = new User("Hong Gil Dong", 30, "010-1234-5678");

        // When
        String json = objectMapper.writeValueAsString(user);
        System.out.println("JSON Result: " + json);

        // Then
        assertThat(json).contains("010-1234-****"); // 마스킹 확인
        assertThat(json).doesNotContain("5678");    // 원본 노출 안됨 확인
    }

    @Test
    @DisplayName("Avro vs JSON: 바이너리 포맷인 Avro가 데이터 크기가 훨씬 작아야 한다")
    void avroSizeTest() throws Exception {
        // Given
        String name = "Hong Gil Dong";
        int age = 30;
        String phone = "010-1234-5678";
        User user = new User(name, age, phone);

        // When 1: JSON 직렬화
        byte[] jsonBytes = objectMapper.writeValueAsBytes(user);
        System.out.println("JSON Size: " + jsonBytes.length + " bytes");

        // When 2: Avro 직렬화
        byte[] avroBytes = avroService.serializeUser(name, age, phone);
        System.out.println("Avro Size: " + avroBytes.length + " bytes");

        // Then
        // Avro는 필드명("name", "age" 등)을 포함하지 않으므로 훨씬 작음
        assertThat(avroBytes.length).isLessThan(jsonBytes.length);
        
        // 역직렬화 검증
        GenericRecord deserialized = avroService.deserializeUser(avroBytes);
        assertThat(deserialized.get("name").toString()).isEqualTo(name);
    }
}
