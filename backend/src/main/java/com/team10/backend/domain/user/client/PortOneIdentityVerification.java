package com.team10.backend.domain.user.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 포트원 V2 본인인증 조회 응답.
 *
 * <pre>
 * {
 *   "status": "VERIFIED",
 *   "verifiedCustomer": {
 *     "name": "홍길동",
 *     "birthDate": "1990-01-01",
 *     "phoneNumber": "01012345678"
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneIdentityVerification(
        String status,
        VerifiedCustomer verifiedCustomer
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifiedCustomer(
            String name,
            String birthDate,
            String phoneNumber
    ) {}

    public boolean isVerified() {
        return "VERIFIED".equals(status);
    }
}
