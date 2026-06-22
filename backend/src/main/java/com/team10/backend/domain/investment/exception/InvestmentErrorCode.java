package com.team10.backend.domain.investment.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InvestmentErrorCode implements ErrorCode {

    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."),
    STOCK_NOT_TRADABLE(HttpStatus.CONFLICT, "거래 가능한 종목이 아닙니다."),

    WATCHLIST_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 관심 종목입니다."),
    WATCHLIST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "관심 종목은 최대 20개까지 등록할 수 있습니다."),

    INVESTMENT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "투자 계좌를 찾을 수 없습니다."),
    INVESTMENT_ACCOUNT_NOT_ACTIVE(HttpStatus.CONFLICT, "활성 투자 계좌가 아닙니다."),

    INVESTMENT_ACCOUNT_PASSWORD_MISMATCH(HttpStatus.FORBIDDEN, "투자 계좌 비밀번호가 일치하지 않습니다."),
    INVESTMENT_ACCOUNT_OPEN_VERIFICATION_KEY_INVALID(HttpStatus.BAD_REQUEST, "투자 계좌 개설 인증키가 유효하지 않습니다."),
    INVESTMENT_ACCOUNT_NUMBER_GENERATION_FAILED(HttpStatus.CONFLICT, "투자 계좌번호 생성에 실패했습니다."),
    INVESTMENT_ACCOUNT_UPDATE_VALUE_REQUIRED(HttpStatus.BAD_REQUEST, "수정할 투자 계좌 정보는 하나 이상 필요합니다."),
    INVESTMENT_ACCOUNT_CASH_BALANCE_NOT_ZERO(HttpStatus.CONFLICT, "예수금이 0원인 투자 계좌만 해지할 수 있습니다."),
    INVESTMENT_ACCOUNT_HOLDING_EXISTS(HttpStatus.CONFLICT, "보유 종목이 없는 투자 계좌만 해지할 수 있습니다."),
    INVESTMENT_ACCOUNT_OPEN_ORDER_EXISTS(HttpStatus.CONFLICT, "미체결 주문이 없는 투자 계좌만 해지할 수 있습니다."),
    IDENTITY_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "본인인증이 필요합니다."),

    INSUFFICIENT_CASH_BALANCE(HttpStatus.CONFLICT, "예수금이 부족합니다."),
    INVALID_CASH_AMOUNT(HttpStatus.BAD_REQUEST, "입/출금액은 양수여야합니다"),

    REALTIME_ORDERBOOK_STREAM_NOT_FOUND(HttpStatus.NOT_FOUND, "실시간 호가 스트림을 찾을 수 없습니다."),
    REALTIME_ORDERBOOK_SUBSCRIPTION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "실시간 호가 구독 가능 종목 수를 초과했습니다."),

    KIS_AUTH_FAILED(HttpStatus.BAD_GATEWAY, "KIS 인증 처리에 실패했습니다."),
    KIS_API_FAILED(HttpStatus.BAD_GATEWAY, "KIS API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
