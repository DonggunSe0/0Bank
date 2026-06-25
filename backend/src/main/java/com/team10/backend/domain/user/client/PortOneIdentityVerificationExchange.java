package com.team10.backend.domain.user.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/** 포트원 V2 본인인증 조회 API 선언적 HTTP 인터페이스. */
public interface PortOneIdentityVerificationExchange {

    @GetExchange("/identity-verifications/{id}")
    PortOneIdentityVerification getIdentityVerification(@PathVariable("id") String identityVerificationId);
}
