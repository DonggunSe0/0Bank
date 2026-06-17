package com.team10.backend.domain.user.client;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 포트원 V2 본인인증 조회 클라이언트 — Authorization: PortOne {API_SECRET} */
@Slf4j
@Component
public class PortOneClient {

    private static final String BASE_URL = "https://api.portone.io";

    private final String apiSecret;
    private final RestClient restClient;

    public PortOneClient(@Value("${portone.api-secret}") String apiSecret) {
        this.apiSecret = apiSecret;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

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
