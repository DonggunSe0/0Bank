package com.team10.backend.domain.user.verification;

/** 1원 계좌인증 송금 서비스 인터페이스 (CodefBankTransferService가 구현) */
public interface BankTransferService {

    /** 사용자 계좌로 1원을 송금하고 입금 메모에 인증코드를 포함시킨다 */
    void sendOneWon(String organization, String accountNumber, String verificationCode);
}
