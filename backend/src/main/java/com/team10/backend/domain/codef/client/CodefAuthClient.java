package com.team10.backend.domain.codef.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** CODEF OAuth Access Token 발급/캐싱 (OCR, 1원 송금 등 CODEF API 호출에서 공용으로 사용) */
@Slf4j
@Component
public class CodefAuthClient {

    private static final String OAUTH_URL = "https://oauth.codef.io/oauth/token";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;

    // AT 캐시 — 만료 5분 전 갱신, token+만료시간 원자적 관리
    private record TokenCache(String token, long expiryEpoch) {}
    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();

    public CodefAuthClient(
            @Value("${codef.client-id}") String clientId,
            @Value("${codef.client-secret}") String clientSecret,
            RestClient restClient
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = restClient;
    }

    /** 캐시된 토큰이 유효하면 재사용, 만료 5분 이내로 남았으면 새로 발급한다 */
    public String getAccessToken() {
        TokenCache cache = tokenCache.get();
        if (cache != null && Instant.now().getEpochSecond() < cache.expiryEpoch() - 300) {
            return cache.token();
        }

        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String response = restClient.post()
                .uri(OAUTH_URL)
                .header("Authorization", "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=read")
                .retrieve()
                .body(String.class);

        try {
            Map<?, ?> tokenMap = OBJECT_MAPPER.readValue(response, Map.class);
            String accessToken = (String) tokenMap.get("access_token");
            Number expiresIn = (Number) tokenMap.get("expires_in");
            long expiryEpoch = Instant.now().getEpochSecond() + expiresIn.longValue();
            tokenCache.set(new TokenCache(accessToken, expiryEpoch));
            log.info("[CODEF] 토큰 발급 완료");
            return accessToken;
        } catch (Exception e) {
            // 호출부에서 각자의 도메인 예외로 변환하도록 그대로 전파 (BusinessException이면 호출부에서 가로채지 못함)
            throw new CodefAuthException("CODEF 토큰 응답 파싱 실패", e);
        }
    }
}
