package com.team10.backend.domain.account.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements ErrorCode {

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.CONFLICT, "활성 계좌가 아닙니다."),
    ACCOUNT_BALANCE_NOT_ZERO(HttpStatus.CONFLICT, "잔액이 0원인 계좌만 해지할 수 있습니다."),
    ACCOUNT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "계좌 접근 권한이 없습니다."),
    IDENTITY_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "본인인증이 필요합니다."),
    ACCOUNT_NUMBER_GENERATION_FAILED(HttpStatus.CONFLICT, "계좌번호 생성에 실패했습니다."),
    //UserErrorCode 임시로 넣어둔 것, 추후 UserErrorCode로 분리 필요
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}