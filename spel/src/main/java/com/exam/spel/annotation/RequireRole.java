package com.exam.spel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SpEL 표현식이 true일 때만 메서드 실행을 허용하는 어노테이션
 *
 * 사용 예) @RequireRole("#user.role == 'ADMIN'")
 * 메서드 파라미터를 #파라미터명 으로 참조
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    // 평가할 SpEL 표현식 (true여야 통과)
    String value();

    String message() default "접근 권한이 없습니다.";
}
