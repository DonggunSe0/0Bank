package com.team10.backend.domain.codef.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.verification.BankTransferService;
import com.team10.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** CODEF API 기반 1원 계좌인증 송금 서비스. */
@Slf4j
@Service
@Primary
public class CodefBankTransferService implements BankTransferService {

    // 운영 전환 시 development.codef.io → api 도메인으로 교체
    private static final String TRANSFER_AUTH_URL =
            "https://development.codef.io/v1/kr/bank/a/account/transfer-authentication";
    // inPrintType=9: 고객사 직접 입력 — inPrintContent에 지정한 코드를 입금자명으로 사용
    private static final String IN_PRINT_TYPE_CUSTOM = "9";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CodefAuthClient codefAuthClient;
    private final RestClient codefBankTransferRestClient;

    // one-won-transfer 용 자격증명으로 발급된 토큰을 사용 (CodefAuthClientConfig 참고).
    public CodefBankTransferService(
            @Qualifier("oneWonTransfer") CodefAuthClient codefAuthClient,
            RestClient codefBankTransferRestClient
    ) {
        this.codefAuthClient = codefAuthClient;
        this.codefBankTransferRestClient = codefBankTransferRestClient;
    }

    @Override
    public void sendOneWon(String organization, String accountNumber, String verificationCode) {
        Map<?, ?> responseMap = requestTransfer(organization, accountNumber, verificationCode);

        Map<?, ?> result;
        String code;
        try {
            result = (responseMap != null) ? (Map<?, ?>) responseMap.get("result") : null;
            code = (result != null) ? (String) result.get("code") : null;
        } catch (ClassCastException e) {
            // CODEF가 200 OK이지만 예상과 다른 모양(필드 타입 불일치 등)으로 응답한 경우.
            // 캐스팅 실패를 그대로 흘리면 GlobalExceptionHandler의 일반 500으로 새어나간다.
            log.error("[CODEF] 1원 송금 응답 형식이 예상과 다름 — org={}, account={}",
                    organization, maskAccountNumber(accountNumber), e);
            throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        if (!"CF-00000".equals(code)) {
            // 계좌번호는 마스킹, 인증코드(verificationCode)는 시연 목적상 의도적으로 평문 노출
            log.error("[CODEF] 1원 송금 실패 — org={}, account={}, code={}, message={}",
                    organization, maskAccountNumber(accountNumber), code, result != null ? result.get("message") : null);
            throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        // 주의: 여기서 찍는 건 CODEF 응답 code("CF-00000")가 아니라 송금 시 입금자명으로 쓴 verificationCode(OTP)다.
        log.info("[CODEF] 1원 송금 완료 — org={}, account={}, verificationCode={}",
                organization, maskAccountNumber(accountNumber), verificationCode);
    }

    /** 1원 송금 인증 API 호출 + 응답 디코딩. 실패 시 전부 ONE_WON_TRANSFER_FAILED로 변환한다. */
    private Map<?, ?> requestTransfer(String organization, String accountNumber, String verificationCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("organization", organization);
        body.put("account", accountNumber);
        body.put("inPrintType", IN_PRINT_TYPE_CUSTOM);
        body.put("inPrintContent", verificationCode);

        try {
            String token = codefAuthClient.getAccessToken();

            String response = codefBankTransferRestClient.post()
                    .uri(TRANSFER_AUTH_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String decoded = URLDecoder.decode(response != null ? response : "", StandardCharsets.UTF_8);
            return OBJECT_MAPPER.readValue(decoded, Map.class);

        } catch (CodefAuthException | RestClientException | IllegalArgumentException | JsonProcessingException e) {
            log.error("[CODEF] 1원 송금 중 오류 — org={}, account={}", organization, maskAccountNumber(accountNumber), e);
            throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }
    }

    /**
     * 로그용 계좌번호 마스킹 (앞 최대 6자리 + 뒤 4자리만 노출, 나머지는 '*').
     * {@code ExAccountCandidateRes.maskAccountNumber}와 동일한 표시 방식 — 기존 마스킹 컨벤션을 따른다.
     */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        int prefixLength = Math.min(6, accountNumber.length() - 4);
        String prefix = accountNumber.substring(0, prefixLength);
        String suffix = accountNumber.substring(accountNumber.length() - 4);
        return prefix + "*".repeat(accountNumber.length() - prefixLength - 4) + suffix;
    }
}
