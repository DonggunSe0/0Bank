package com.team10.backend.domain.user.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 실제 은행 API 대신 사용하는 1원 송금 Mock 서비스. */
@Slf4j
@Service
public class MockBankTransferService implements BankTransferService {

    /** 사용자 계좌로 1원을 송금하고 입금 메모에 인증코드를 포함시킨다 */
    public void sendOneWon(String organization, String accountNumber, String verificationCode) {
        log.info("[1원 송금] 기관={}, 계좌={}, 인증코드={}, 금액=1원 — Mock 송금 완료", organization, accountNumber, verificationCode);
        // 실제 연동 시 은행 API 호출
    }
}
