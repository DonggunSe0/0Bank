package com.team10.backend.domain.exchange.type;

public enum CurrencyStatus {
    ACTIVE,     // 조회/거래 가능
    INACTIVE,   // 신규 거래 불가
    SUSPENDED   // 일시 중단
}
