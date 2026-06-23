package com.team10.backend.domain.user.client;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/** 포트원 V2 본인인증 조회 클라이언트. */
@Slf4j
@Component
public class PortOneClient {

    private final PortOneIdentityVerificationExchange exchange;

    public PortOneClient(PortOneIdentityVerificationExchange exchange) {
        this.exchange = exchange;
    }

    public PortOneIdentityVerification getIdentityVerification(String identityVerificationId) {
        PortOneIdentityVerification result;
        try {
            result = exchange.getIdentityVerification(identityVerificationId);
        } catch (ResourceAccessException e) {
            // 타임아웃/연결거부 — HTTP 응답 자체가 없어 PortOneHttpServiceConfig의
            // defaultStatusHandler(상태 코드 기반)가 잡지 못하는 케이스만 별도로 처리한다.
            log.error("[PortOne] 본인인증 조회 연결 실패 — id={}, error={}", identityVerificationId, e.getMessage());
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        if (result == null) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        log.debug("[PortOne] 본인인증 조회 완료 — id={}, status={}", identityVerificationId, result.status());
        return result;
    }
}
