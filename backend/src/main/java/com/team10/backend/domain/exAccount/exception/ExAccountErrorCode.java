package com.team10.backend.domain.exAccount.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExAccountErrorCode implements ErrorCode {

    EX_ACCOUNT_SYNC_ITEMS_REQUIRED(HttpStatus.BAD_REQUEST, "동기화할 외부 계좌 목록이 필요합니다."),
    EX_ACCOUNT_SYNC_REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "외부 계좌 동기화 필수값이 누락되었습니다."),
    EX_ACCOUNT_TRANSACTION_SYNC_ITEMS_REQUIRED(HttpStatus.BAD_REQUEST, "새로고침할 외부 계좌 거래내역 목록이 필요합니다."),
    EX_ACCOUNT_TRANSACTION_SYNC_REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "외부 계좌 거래내역 새로고침 필수값이 누락되었습니다."),
    EX_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "외부 계좌를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
