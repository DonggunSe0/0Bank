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
    INVALID_DEPOSIT_AMOUNT(HttpStatus.BAD_REQUEST, "예금 가입 금액이 올바르지 않습니다."),
    INVALID_INSTALLMENT_AMOUNT(HttpStatus.BAD_REQUEST, "적금 월 납입액이 올바르지 않습니다."),
    INVALID_TARGET_AMOUNT(HttpStatus.BAD_REQUEST, "적금 목표 금액이 올바르지 않습니다."),
    INSTALLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "적금 가입 정보를 찾을 수 없습니다."),
    INVALID_SAVING_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 저축 타입입니다."),
    SAVING_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "가입중 상태에서만 중도 해지할 수 있습니다."),
    SAVING_MATURITY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "가입중 상태에서만 만기 처리할 수 있습니다."),
    SAVING_NOT_MATURED_YET(HttpStatus.BAD_REQUEST, "아직 만기일이 도래하지 않았습니다.");

    private final HttpStatus status;
    private final String message;
}
