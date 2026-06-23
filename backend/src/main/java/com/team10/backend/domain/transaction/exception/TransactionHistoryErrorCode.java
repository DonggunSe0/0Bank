package com.team10.backend.domain.transaction.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TransactionHistoryErrorCode implements ErrorCode {
    TRANSACTION_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "거래내역을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
