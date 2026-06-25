package com.team10.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 없습니다."),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 키 형식입니다."),
    IDEMPOTENCY_REQUEST_CONFLICT(HttpStatus.CONFLICT, "같은 키인데 요청 내용이 다릅니다."),
    IDEMPOTENCY_REQUEST_PROCESSING(HttpStatus.CONFLICT, "같은 키 요청이 아직 처리 중입니다."),
    IDEMPOTENCY_REQUEST_FAILED(HttpStatus.CONFLICT, "같은 키 요청은 이미 실패 처리되었습니다."),
    IDEMPOTENCY_REQUEST_EXPIRED(HttpStatus.CONFLICT, "만료된 멱등성 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

}
