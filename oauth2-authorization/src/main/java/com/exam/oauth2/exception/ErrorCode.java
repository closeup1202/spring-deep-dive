package com.exam.oauth2.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인별 에러 코드 정의
 *
 * epki-auth의 ErrorCodes enum을 참고해 학습용으로 단순화한 버전.
 * 실무에서는 에러 코드를 체계적으로 분류 (EA10001, EA20001 등)해
 * 운영팀이 로그만 보고 원인을 파악할 수 있게 한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 클라이언트 관련
    CLIENT_NOT_FOUND("C001", "등록되지 않은 클라이언트입니다.", HttpStatus.NOT_FOUND),
    CLIENT_INACTIVE("C002", "비활성화된 클라이언트입니다.", HttpStatus.UNAUTHORIZED),
    CLIENT_SECRET_EXPIRED("C003", "만료된 클라이언트 시크릿입니다.", HttpStatus.UNAUTHORIZED),

    // 인증 코드 관련 (AuthCodeStore / Redis)
    INVALID_AUTH_CODE("A001", "유효하지 않거나 만료된 인증 코드입니다.", HttpStatus.UNAUTHORIZED),

    // 요청 검증
    INVALID_REQUEST_METHOD("R001", "지원하지 않는 HTTP 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    INVALID_REFERER("R002", "허용되지 않은 Referer 헤더입니다.", HttpStatus.UNAUTHORIZED),
    MISSING_PARAMETER("R003", "필수 파라미터가 누락되었습니다.", HttpStatus.BAD_REQUEST),

    // 시스템 에러
    INTERNAL_SERVER_ERROR("S001", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
