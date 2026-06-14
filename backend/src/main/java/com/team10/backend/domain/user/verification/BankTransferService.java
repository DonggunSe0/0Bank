package com.team10.backend.domain.user.verification;

/**
 * 1원 계좌인증을 위한 송금 서비스 인터페이스.
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link MockBankTransferService} — 로컬/테스트 환경용 Mock</li>
 *   <li>{@code CodefBankTransferService} — CODEF API 실연동 (운영/개발 환경)</li>
 * </ul>
 */
public interface BankTransferService {

    /**
     * 사용자 계좌로 1원을 송금하고 입금 메모에 인증코드를 포함시킨다.
     *
     * @param accountNumber    수신 계좌번호
     * @param verificationCode 입금 메모에 포함할 4자리 인증코드
     */
    void sendOneWon(String organization, String accountNumber, String verificationCode);
}
