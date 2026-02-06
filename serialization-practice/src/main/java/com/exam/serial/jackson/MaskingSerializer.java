package com.exam.serial.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

// 문자열을 받아서 뒤의 4자리를 마스킹하는 직렬화기
public class MaskingSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        if (value.length() <= 4) {
            gen.writeString("****");
            return;
        }

        // 앞부분은 그대로, 뒤 4자리는 *로 변경
        String masked = value.substring(0, value.length() - 4) + "****";
        gen.writeString(masked);
    }
}
