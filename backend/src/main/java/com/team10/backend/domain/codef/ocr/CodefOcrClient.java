package com.team10.backend.domain.codef.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.client.CodefAuthClient;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

// CODEF EasyCodef SDK는 URL 인코딩 + Content-Type 누락으로 CF-00003 오류 발생
// → Spring RestClient로 application/json 방식 직접 호출

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
        try {
            String token = codefAuthClient.getAccessToken();
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

            Map<?, ?> responseMap = OBJECT_MAPPER.readValue(decoded, Map.class);
            if (responseMap == null) {
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            Map<?, ?> result = (Map<?, ?>) responseMap.get("result");
            if (result == null) {
                throw new BusinessException(UserErrorCode.OCR_FAILED);
            }

            String code = (String) result.get("code");
            log.debug("[CODEF OCR] 응답 수신 — code={}", code);
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
                log.warn("[CODEF OCR] 필수 필드 누락 — name={}, identity={}, date={}", name, maskIdentity(rawIdentity), rawDate);
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
