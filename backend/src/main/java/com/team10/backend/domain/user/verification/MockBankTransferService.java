package com.team10.backend.domain.user.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 실제 은행 API 대신 사용하는 1원 송금 Mock 서비스.
 *
 * <p>실제 연동 시 이 클래스를 은행 Open API 클라이언트로 교체한다.
 */
@Slf4j
@Service
public class MockBankTransferService {

    /**
     * 사용자 계좌로 1원을 송금하고 입금 메모에 인증코드를 포함시킨다.
     *
     * @param accountNumber 수신 계좌번호
     * @param verificationCode 입금 메모에 포함할 4자리 인증코드
     */
    public void sendOneWon(String accountNumber, String verificationCode) {
        log.info("[1원 송금] 계좌={}, 인증코드={}, 금액=1원 — Mock 송금 완료", accountNumber, verificationCode);
        // 실제 연동 시 은행 API 호출
    }
}
