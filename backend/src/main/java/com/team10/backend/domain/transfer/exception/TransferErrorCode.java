package com.team10.backend.domain.transfer.exception;


import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TransferErrorCode implements ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    ACCOUNT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "계좌 접근 권한이 없습니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.CONFLICT, "활성 계좌가 아닙니다."),
    ACCOUNT_PASSWORD_NOT_SET(HttpStatus.CONFLICT, "계좌 비밀번호가 설정되지 않았습니다."),
    ACCOUNT_PASSWORD_MISMATCH(HttpStatus.FORBIDDEN, "계좌 비밀번호가 일치하지 않습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "잔액이 부족합니다."),
    TRANSFER_FAILED(HttpStatus.CONFLICT, "송금 처리에 실패했습니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
