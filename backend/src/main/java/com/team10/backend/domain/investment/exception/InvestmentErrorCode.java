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
    IDENTITY_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "본인인증이 필요합니다."),

    INSUFFICIENT_CASH_BALANCE(HttpStatus.CONFLICT, "예수금이 부족합니다."),
    INSUFFICIENT_HOLDING_QUANTITY(HttpStatus.CONFLICT, "보유 수량이 부족합니다."),
    INVALID_CASH_AMOUNT(HttpStatus.BAD_REQUEST, "입/출금액은 양수여야합니다"),
    INVALID_ORDER_QUANTITY(HttpStatus.BAD_REQUEST, "주문 수량은 양수여야 합니다."),
    INVALID_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "주문 금액이 유효하지 않습니다."),
    MARKET_CLOSED(HttpStatus.CONFLICT, "현재 거래 가능한 장 운영 시간이 아닙니다."),
    ORDER_PRICE_UNAVAILABLE(HttpStatus.CONFLICT, "주문 체결에 사용할 최신 호가가 없습니다."),
    ORDER_PRICE_STALE(HttpStatus.CONFLICT, "주문 체결에 사용할 호가가 만료되었습니다."),
    PRICE_DEVIATION_EXCEEDED(HttpStatus.CONFLICT, "기대 가격 대비 체결 가격 편차가 허용 범위를 초과했습니다."),
    ORDER_REQUIRES_ACTIVE_REALTIME_SUBSCRIPTION(HttpStatus.CONFLICT, "실시간 호가를 구독 중인 종목만 주문할 수 있습니다."),
    INVESTMENT_TRADE_DUPLICATED(HttpStatus.CONFLICT, "이미 처리된 투자 주문입니다."),

    REALTIME_ORDERBOOK_STREAM_NOT_FOUND(HttpStatus.NOT_FOUND, "실시간 호가 스트림을 찾을 수 없습니다."),
    REALTIME_ORDERBOOK_SUBSCRIPTION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "실시간 호가 구독 가능 종목 수를 초과했습니다."),

    KIS_AUTH_FAILED(HttpStatus.BAD_GATEWAY, "KIS 인증 처리에 실패했습니다."),
    KIS_API_FAILED(HttpStatus.BAD_GATEWAY, "KIS API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
