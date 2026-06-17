package com.team10.backend.domain.user.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

// CODEF EasyCodef SDK는 URL 인코딩 + Content-Type 누락으로 CF-00003 오류 발생
// → Spring RestClient로 application/json 방식 직접 호출

/** CODEF 신분증 OCR 클라이언트 (POST /v1/kr/etc/a/ocr/registration-card) */
@Slf4j
@Component
public class CodefOcrClient {

    private static final String OAUTH_URL = "https://oauth.codef.io/oauth/token";
    private static final String OCR_URL = "https://development.codef.io/v1/kr/etc/a/ocr/registration-card";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;

    // AT 캐시 — 만료 5분 전 갱신, token+만료시간 원자적 관리
    private record TokenCache(String token, long expiryEpoch) {}
    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();

    public CodefOcrClient(
            @Value("${codef.client-id}") String clientId,
            @Value("${codef.client-secret}") String clientSecret,
            RestClient restClient
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = restClient;
    }

    /**
     * 신분증 이미지 바이트를 CODEF OCR API로 전송하고 구조화된 정보를 반환한다.
     */
    public IdCardOcrResult extractIdCard(byte[] imageBytes) {
        try {
            String token = getAccessToken();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, String> body = new HashMap<>();
            body.put("Type", "0");
            body.put("secret_mode", "0");
            body.put("IdCard_base64", base64Image);
            body.put("image_return", "0");
            body.put("image_save", "0");

            String response = restClient.post()
                    .uri(OCR_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String decoded = URLDecoder.decode(response != null ? response : "", StandardCharsets.UTF_8);
            log.debug("[CODEF OCR] 응답 — {}", decoded.substring(0, Math.min(200, decoded.length())));

            Map<?, ?> responseMap = OBJECT_MAPPER.readValue(decoded, Map.class);
            if (responseMap == null) {
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            Map<?, ?> result = (Map<?, ?>) responseMap.get("result");
            if (result == null) {
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            String code = (String) result.get("code");
            if (!"CF-00000".equals(code)) {
                log.error("[CODEF OCR] 실패 — code={}, message={}", code, result.get("message"));
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            Map<?, ?> data = (Map<?, ?>) responseMap.get("data");
            if (data == null) {
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            String name        = (String) data.get("resUserName");
            String rawIdentity = (String) data.get("resUserIdentity");
            String rawDate     = (String) data.get("resIssueDate");

            if (isBlank(name) || isBlank(rawIdentity) || isBlank(rawDate)
                    || rawIdentity.length() < 13 || rawDate.length() < 8) {
                log.warn("[CODEF OCR] 필수 필드 누락 — name={}, identity={}, date={}", name, rawIdentity, rawDate);
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            String residentNumber = rawIdentity.substring(0, 6) + "-" + rawIdentity.substring(6);
            String issueDate = rawDate.substring(0, 4) + "-" + rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8);

            log.info("[CODEF OCR] 추출 완료 — name={}", name);
            return new IdCardOcrResult(name, residentNumber, issueDate);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CODEF OCR] 처리 오류", e);
            throw new BusinessException(UserErrorCode.OCR_FAILED);
        }
    }

    private String getAccessToken() {
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
            log.info("[CODEF OCR] 토큰 발급 완료");
            return accessToken;
        } catch (Exception e) {
            throw new BusinessException(UserErrorCode.OCR_FAILED);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
