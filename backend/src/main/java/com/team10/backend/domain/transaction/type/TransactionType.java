package com.team10.backend.domain.transaction.type;

public enum TransactionType {
    DEPOSIT, // 입금 [ 서비스 상에서의 ]
    TRANSFER, // 계좌 이체
    EXCHANGE, // 환전
    PAYMENT, // 결제 [ 추후 서비스 상의 물건 결제 구현 염두 ]
    /*
    TODO : REFUND, FEE, INTEREST, TAX ... 추가 기능 개발 시 도입 고려
    */
}
