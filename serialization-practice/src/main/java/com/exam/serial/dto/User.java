package com.exam.serial.dto;

import com.exam.serial.jackson.MaskingSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private String name;
    private int age;

    // 이 필드는 JSON으로 변환될 때 MaskingSerializer를 거침
    @JsonSerialize(using = MaskingSerializer.class)
    private String phoneNumber;
}
