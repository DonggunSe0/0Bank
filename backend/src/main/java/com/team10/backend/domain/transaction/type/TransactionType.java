package com.team10.backend.domain.transaction.type;

public enum TransactionType {
    DEPOSIT, // 입금 [ 서비스 상에서의 ]
    TRANSFER, // 계좌 이체
    EXCHANGE, // 환전
    PAYMENT, // 결제 [ 추후 서비스 상의 물건 결제 구현 염두 ]
    SAVING_DEPOSIT_SIGNUP, // 예금 가입 금액 이동
    SAVING_INSTALLMENT_SIGNUP, // 적금 가입 1회차 납입금 이동
    SAVING_CANCEL_REFUND, // 예금/적금 중도 해지 반환금 입금
    SAVING_MATURITY, // 예금/적금 만기 지급금 입금
    INSTALLMENT_PAYMENT, // 적금 월 납입 자동이체 출금
    /*
    TODO : REFUND, FEE, INTEREST, TAX ... 추가 기능 개발 시 도입 고려
    */
}
