package com.exam.spel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SpEL 표현식으로 동적 로그 메시지를 생성하는 어노테이션
 *
 * 사용 예) @AuditLog("'주문 생성 - 사용자: ' + #user.name + ', 금액: ' + #result.amount")
 * #result 로 메서드 반환값 참조 가능
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    // 평가할 SpEL 표현식
    String value();
}
