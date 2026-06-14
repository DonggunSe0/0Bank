package com.team10.backend.domain.user.client;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 포트원 V2 REST API 클라이언트.
 *
 * <h2>인증 방식</h2>
 * <pre>Authorization: PortOne {API_SECRET}</pre>
 *
 * <h2>사용 API</h2>
 * <pre>GET https://api.portone.io/identity-verifications/{identityVerificationId}</pre>
 */
@Slf4j
@Component
public class PortOneClient {

    private static final String BASE_URL = "https://api.portone.io";

    private final String apiSecret;
    private final RestClient restClient;

    public PortOneClient(@Value("${portone.api-secret}") String apiSecret) {
        this.apiSecret = apiSecret;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * 본인인증 결과를 조회한다.
     *
     * @param identityVerificationId 프론트엔드에서 포트원 SDK로 발급받은 인증 ID
     * @return 포트원 본인인증 결과
     * @throws BusinessException 조회 실패 또는 네트워크 오류 시
     */
    public PortOneIdentityVerification getIdentityVerification(String identityVerificationId) {
        try {
            PortOneIdentityVerification result = restClient.get()
                    .uri("/identity-verifications/{id}", identityVerificationId)
                    .header("Authorization", "PortOne " + apiSecret)
                    .retrieve()
                    .body(PortOneIdentityVerification.class);

            if (result == null) {
                throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
            }

            log.debug("[PortOne] 본인인증 조회 완료 — id={}, status={}", identityVerificationId, result.status());
            return result;

        } catch (RestClientException e) {
            log.error("[PortOne] 본인인증 조회 실패 — id={}, error={}", identityVerificationId, e.getMessage());
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
    }
}
