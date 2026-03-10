package com.exam.oauth2.exception;

import lombok.Getter;

/**
 * 애플리케이션 공통 예외 클래스
 *
 * 도메인 예외를 ErrorCode와 함께 래핑해 일관된 에러 처리를 지원.
 * Spring Security의 AuthenticationException 계열이 아닌 경우에 사용.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " - " + detail);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
