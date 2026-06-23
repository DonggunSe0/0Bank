package com.team10.backend.domain.exchange.type;

public enum ExchangeOrderStatus {
    REQUESTED, // 환전 주문이 생성되었지만 아직 최종 처리되지 않은 상태
    COMPLETED, // 환전 주문이 정상적으로 완료되어 계좌/지갑 잔액 반영이 끝난 상태
    FAILED,    // 환전 처리 중 잔액 부족, 시스템 오류 등으로 실패한 상태
    CANCELED   // 사용자 요청 또는 정책에 의해 환전 주문이 취소된 상태
}
