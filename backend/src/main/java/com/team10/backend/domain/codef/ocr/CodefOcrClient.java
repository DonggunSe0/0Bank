package com.team10.backend.domain.codef.ocr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.client.CodefAuthClient;
import com.team10.backend.domain.codef.client.CodefAuthException;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** CODEF 신분증 OCR 클라이언트 (POST /v1/kr/etc/a/ocr/registration-card) */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodefOcrClient {

    private static final String OCR_URL = "https://development.codef.io/v1/kr/etc/a/ocr/registration-card";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CodefAuthClient codefAuthClient;
    private final RestClient restClient;

    /**
     * 신분증 이미지 바이트를 CODEF OCR API로 전송하고 구조화된 정보를 반환한다.
     */
    public IdCardOcrResult extractIdCard(byte[] imageBytes) {
        Map<?, ?> responseMap = requestOcr(imageBytes);

        try {
            Map<?, ?> result = (responseMap != null) ? (Map<?, ?>) responseMap.get("result") : null;
            String code = (result != null) ? (String) result.get("code") : null;
            log.debug("[CODEF OCR] 응답 수신 — code={}", code);

            if (!"CF-00000".equals(code)) {
                log.error("[CODEF OCR] 실패 — code={}, message={}", code, result != null ? result.get("message") : null);
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
                log.warn("[CODEF OCR] 필수 필드 누락 — name={}, identity={}, date={}", name, maskIdentity(rawIdentity), rawDate);
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            String residentNumber = rawIdentity.substring(0, 6) + "-" + rawIdentity.substring(6);
            String issueDate = rawDate.substring(0, 4) + "-" + rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8);

            log.info("[CODEF OCR] 추출 완료 — name={}", name);
            return new IdCardOcrResult(name, residentNumber, issueDate);

        } catch (ClassCastException e) {
            // CODEF가 200 OK이지만 예상과 다른 모양(필드 타입 불일치 등)으로 응답한 경우.
            // 캐스팅 실패를 그대로 흘리면 GlobalExceptionHandler의 일반 500으로 새어나간다.
            log.error("[CODEF OCR] 응답 형식이 예상과 다름", e);
            throw new BusinessException(UserErrorCode.OCR_FAILED);
        }
    }

    /** OCR API 호출 + 응답 디코딩. 실패 시 전부 OCR_FAILED로 변환한다. */
    private Map<?, ?> requestOcr(byte[] imageBytes) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        Map<String, String> body = new HashMap<>();
        body.put("Type", "0");
        body.put("secret_mode", "0");
        body.put("IdCard_base64", base64Image);
        body.put("image_return", "0");
        body.put("image_save", "0");

        try {
            String token = codefAuthClient.getAccessToken();

            String response = restClient.post()
                    .uri(OCR_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String decoded = URLDecoder.decode(response != null ? response : "", StandardCharsets.UTF_8);
            return OBJECT_MAPPER.readValue(decoded, Map.class);

        } catch (CodefAuthException | RestClientException | IllegalArgumentException | JsonProcessingException e) {
            log.error("[CODEF OCR] 처리 오류", e);
            throw new BusinessException(UserErrorCode.OCR_FAILED);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 주민등록번호 등 식별 정보를 로그용으로 마스킹한다 (앞 6자리만 노출). */
    private String maskIdentity(String raw) {
        if (raw == null || raw.length() < 6) {
            return "***";
        }
        return raw.substring(0, 6) + "-*******";
    }
}
