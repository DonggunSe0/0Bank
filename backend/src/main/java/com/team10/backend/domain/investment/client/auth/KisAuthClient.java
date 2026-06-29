package com.team10.backend.domain.investment.client.auth;

import static com.team10.backend.domain.investment.config.KisConstants.Auth.TOKEN_ISSUE_PATH;
import static com.team10.backend.domain.investment.config.KisConstants.Auth.TOKEN_REVOKE_PATH;
import static com.team10.backend.domain.investment.config.KisConstants.Auth.WEBSOCKET_APPROVAL_PATH;
import static com.team10.backend.domain.investment.config.KisConstants.BASE_URL;
import static com.team10.backend.domain.investment.config.KisConstants.Header.APP_KEY;
import static com.team10.backend.domain.investment.config.KisConstants.Header.APP_SECRET;
import static com.team10.backend.domain.investment.config.KisConstants.Header.SECRET_KEY;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team10.backend.domain.investment.client.auth.dto.KisAccessToken;
import com.team10.backend.domain.investment.client.auth.dto.KisWebSocketApprovalKey;
import com.team10.backend.domain.investment.config.KisProperties;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RestClient를 통해 KIS의 AccessToken, Websocket 관련 Auth 요청을 호출하는 클래스
 */

@Component
@Slf4j
@RequiredArgsConstructor
public class KisAuthClient {

    private static final DateTimeFormatter TOKEN_EXPIRED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final KisProperties properties;

    /**
     * 엑세스 토큰 발급 API
     */
    public KisAccessToken issueAccessToken() {
        KisAccessTokenRes response = restClient.post()
                .uri(BASE_URL + TOKEN_ISSUE_PATH)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        APP_KEY, properties.appKey(),
                        APP_SECRET, properties.appSecret()
                ))
                .retrieve()
                .body(KisAccessTokenRes.class);

        if (response == null || response.accessToken() == null || response.accessTokenExpiredAt() == null) {
            throw new BusinessException(InvestmentErrorCode.KIS_AUTH_FAILED);
        }

        log.debug("KIS AccessToken issued. expiresAt={}", response.accessTokenExpiredAt());

        return new KisAccessToken(
                response.accessToken(),
                LocalDateTime.parse(
                        response.accessTokenExpiredAt(),
                        TOKEN_EXPIRED_AT_FORMATTER
                )
        );
    }

    /**
     * 웹소켓 접속키 발급 API
     */
    public KisWebSocketApprovalKey issueWebSocketApprovalKey() {
        KisWebSocketApprovalKeyRes response = restClient.post()
                .uri(BASE_URL + WEBSOCKET_APPROVAL_PATH)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        APP_KEY, properties.appKey(),
                        SECRET_KEY, properties.appSecret()
                ))
                .retrieve()
                .body(KisWebSocketApprovalKeyRes.class);

        if (response == null || response.approvalKey() == null) {
            throw new BusinessException(InvestmentErrorCode.KIS_AUTH_FAILED);
        }

        log.info("KIS WebSocket approval key issued");

        return new KisWebSocketApprovalKey(response.approvalKey());
    }

    /**
     * 엑세스 토큰 폐기 API
     */
    public void revokeAccessToken(String accessToken) {
        restClient.post()
                .uri(BASE_URL + TOKEN_REVOKE_PATH)
                .body(Map.of(
                        APP_KEY, properties.appKey(),
                        APP_SECRET, properties.appSecret(),
                        "token", accessToken
                ))
                .retrieve()
                .toBodilessEntity();

        log.debug("KIS AccessToken revoked");
    }

    private record KisAccessTokenRes(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("access_token_token_expired") String accessTokenExpiredAt
    ) {
    }

    private record KisWebSocketApprovalKeyRes(
            @JsonProperty("approval_key") String approvalKey
    ) {
    }
}
