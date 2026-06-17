package com.team10.backend.domain.saving.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SavingErrorCode implements ErrorCode {

    SAVING_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "저축 상품을 찾을 수 없습니다."),
    DEPOSIT_NOT_FOUND(HttpStatus.NOT_FOUND, "예금 가입 정보를 찾을 수 없습니다."),
    INVALID_DEPOSIT_AMOUNT(HttpStatus.BAD_REQUEST, "예금 가입 금액이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
