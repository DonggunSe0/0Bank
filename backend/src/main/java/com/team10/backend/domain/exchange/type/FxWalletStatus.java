package com.team10.backend.domain.exchange.type;

public enum FxWalletStatus {
    ACTIVE,     // 입출금 및 환전 거래에 사용할 수 있는 정상 지갑 상태
    SUSPENDED,  // 일시적으로 거래가 제한된 지갑 상태
    CLOSED      // 해지되어 더 이상 사용할 수 없는 지갑 상태
}
