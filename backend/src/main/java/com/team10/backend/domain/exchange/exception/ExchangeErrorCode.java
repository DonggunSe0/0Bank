package com.team10.backend.domain.exchange.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExchangeErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CURRENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "통화를 찾을 수 없습니다."),
    CURRENCY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 통화입니다."),
    EXCHANGE_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "환율 정보를 찾을 수 없습니다."),
    EXCHANGE_RATE_SYNC_FAILED(HttpStatus.BAD_GATEWAY, "환율 정보 동기화에 실패했습니다."),
    PARTIAL_CACHE(HttpStatus.BAD_GATEWAY, "환율 캐시가 일부만 존재합니다."),

    FX_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "외화 지갑을 찾을 수 없습니다."),
    FX_WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 생성된 외화 지갑입니다."),
    FX_WALLET_NOT_ACTIVE(HttpStatus.CONFLICT, "활성 상태의 외화 지갑이 아닙니다."),
    FX_WALLET_ACCESS_DENIED(HttpStatus.FORBIDDEN, "외화 지갑 접근 권한이 없습니다."),
    INSUFFICIENT_FX_BALANCE(HttpStatus.CONFLICT, "외화 지갑 잔액이 부족합니다."),
    FX_WALLET_BALANCE_REMAINING(HttpStatus.CONFLICT, "외화 지갑 잔액이 남아있어 해지할 수 없습니다."),
    FX_WALLET_CURRENCY_MISMATCH(HttpStatus.CONFLICT, "외화 지갑의 통화가 환전 견적의 외화 통화와 일치하지 않습니다."),

    EXCHANGE_QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "환전 견적을 찾을 수 없습니다."),
    EXCHANGE_QUOTE_EXPIRED(HttpStatus.GONE, "환전 견적이 만료되었습니다."),
    EXCHANGE_QUOTE_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 환전 견적입니다."),
    EXCHANGE_QUOTE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "환전 견적 접근 권한이 없습니다."),

    EXCHANGE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "환전 주문을 찾을 수 없습니다."),
    EXCHANGE_ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "환전 주문 접근 권한이 없습니다."),
    EXCHANGE_ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 환전 주문입니다."),
    DUPLICATE_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "이미 처리된 멱등성 키입니다."),

    INVALID_EXCHANGE_AMOUNT(HttpStatus.BAD_REQUEST, "환전 금액이 올바르지 않습니다."),
    INVALID_EXCHANGE_DIRECTION(HttpStatus.BAD_REQUEST, "환전 방향이 올바르지 않습니다."),
    SAME_CURRENCY_EXCHANGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "동일 통화 간 환전은 허용되지 않습니다."),
    EXCHANGE_PROCESSING_FAILED(HttpStatus.CONFLICT, "환전 처리에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
