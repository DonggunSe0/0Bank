package com.team10.backend.domain.user.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    IDENTITY_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 본인인증이 완료된 사용자입니다."),
    OCR_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "신분증 이미지를 첨부해야 합니다."),
    OCR_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 크기가 허용 범위를 초과합니다. (최대 10MB)"),
    OCR_IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. (허용: jpeg, png)"),
    VERIFICATION_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "본인인증 세션을 찾을 수 없습니다."),
    VERIFICATION_NOT_READY_FOR_ONE_WON(HttpStatus.CONFLICT, "행안부 인증이 완료된 후 1원 송금 인증을 진행할 수 있습니다."),
    ONE_WON_CODE_EXPIRED(HttpStatus.GONE, "인증코드가 만료되었습니다. 1원 송금을 다시 요청해주세요."),
    ONE_WON_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증코드가 일치하지 않습니다."),
    ONE_WON_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다. 1원 송금을 다시 요청해주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 Refresh Token입니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
