package com.team10.backend.domain.codef.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.verification.BankTransferService;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// EasyCodef SDK는 URL 인코딩 + Content-Type 누락으로 CF-00003 오류 발생 (CodefOcrClient와 동일한 이슈)
// → Spring RestClient로 application/json 방식 직접 호출

/** CODEF API 기반 1원 계좌인증 송금 서비스. {@code @Primary}로 Mock 대신 자동 주입된다. */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class CodefBankTransferService implements BankTransferService {

    // 운영 전환 시 development.codef.io → api 도메인으로 교체
    private static final String TRANSFER_AUTH_URL =
            "https://development.codef.io/v1/kr/bank/a/account/transfer-authentication";
    // inPrintType=9: 고객사 직접 입력 — inPrintContent에 지정한 코드를 입금자명으로 사용
    private static final String IN_PRINT_TYPE_CUSTOM = "9";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CodefAuthClient codefAuthClient;
    // 1원 송금은 CODEF가 실제 은행 응답을 기다리는 동기 호출이라 전역 5초 timeout보다 긴
    // 전용 RestClient(CodefBankRestClientConfig, read timeout 30s)를 사용한다.
    private final RestClient codefBankTransferRestClient;

    @Override
    public void sendOneWon(String organization, String accountNumber, String verificationCode) {
        try {
            String token = codefAuthClient.getAccessToken();

            Map<String, Object> body = new HashMap<>();
            body.put("organization", organization);
            body.put("account", accountNumber);
            body.put("inPrintType", IN_PRINT_TYPE_CUSTOM);
            body.put("inPrintContent", verificationCode);

            String response = codefBankTransferRestClient.post()
                    .uri(TRANSFER_AUTH_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String decoded = URLDecoder.decode(response != null ? response : "", StandardCharsets.UTF_8);
            log.debug("[CODEF] 1원 이체 응답 — {}", decoded);

            Map<?, ?> responseMap = OBJECT_MAPPER.readValue(decoded, Map.class);
            if (responseMap == null) {
                throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
            }
            Map<?, ?> result = (Map<?, ?>) responseMap.get("result");
            if (result == null) {
                throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
            }
            String code = (String) result.get("code");

            if (!"CF-00000".equals(code)) {
                String message = (String) result.get("message");
                log.error("[CODEF] 1원 송금 실패 — org={}, account={}, code={}, message={}",
                        organization, accountNumber, code, message);
                throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
            }

            log.info("[CODEF] 1원 송금 완료 — org={}, account={}, code={}", organization, accountNumber, verificationCode);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CODEF] 1원 송금 중 오류 — org={}, account={}", organization, accountNumber, e);
            throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }
    }
}
