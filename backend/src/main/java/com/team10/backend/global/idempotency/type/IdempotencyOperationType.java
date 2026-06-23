package com.team10.backend.global.idempotency.type;

public enum IdempotencyOperationType {
    TRANSFER,               // 송금
    TOPUP,                  // 입금
    EXCHANGE_ORDER,         // 환전 주문
    ONE_WON_VERIFICATION,   // 1원 인증
    INVESTMENT_MARKET_ORDER // 투자 시장가 주문
}
