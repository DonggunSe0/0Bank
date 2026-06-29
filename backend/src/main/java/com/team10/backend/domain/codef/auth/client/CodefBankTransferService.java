package com.team10.backend.domain.codef.auth.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.auth.client.CodefBankTransferExchange.CodefBankTransferRequest;
import com.team10.backend.domain.codef.auth.client.CodefBankTransferExchange.CodefBankTransferResponse;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.verification.BankTransferService;
import com.team10.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** CODEF API 기반 1원 계좌인증 송금 서비스. */
@Slf4j
@Service
@Primary
public class CodefBankTransferService implements BankTransferService {

    // inPrintType=9: 고객사 직접 입력 — inPrintContent에 지정한 코드를 입금자명으로 사용
    private static final String IN_PRINT_TYPE_CUSTOM = "9";

    // 로그 마스킹 — 길이와 무관하게 중간 최소 MIN_MASK_LENGTH자리는 항상 가린다.
    // (ExAccountSyncService.maskAccountNumber와 동일한 컨벤션. 기존엔 최소 마스킹 길이 보장이 없어서
    //  10~11자리 계좌번호(국내 은행 흔한 자릿수)는 거의 또는 전혀 마스킹되지 않는 문제가 있었다.)
    private static final int MAX_VISIBLE_PREFIX_LENGTH = 6;
    private static final int MAX_VISIBLE_SUFFIX_LENGTH = 4;
    private static final int MIN_MASK_LENGTH = 3;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CodefBankTransferExchange codefBankTransferExchange;

    public CodefBankTransferService(CodefBankTransferExchange codefBankTransferExchange) {
        this.codefBankTransferExchange = codefBankTransferExchange;
    }

    @Override
    public void sendOneWon(String organization, String accountNumber, String verificationCode) {
        CodefBankTransferResponse response = requestTransfer(organization, accountNumber, verificationCode);

        CodefApiResult result = (response != null) ? response.result() : null;
        String code = (result != null) ? result.code() : null;

        if (!"CF-00000".equals(code)) {
            // 계좌번호는 마스킹, 인증코드(verificationCode)는 시연 목적상 의도적으로 평문 노출
            log.error("[CODEF] 1원 송금 실패 — org={}, account={}, code={}, message={}",
                    organization, maskAccountNumber(accountNumber), code, result != null ? result.message() : null);
            throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        // 주의: 여기서 찍는 건 CODEF 응답 code("CF-00000")가 아니라 송금 시 입금자명으로 쓴 verificationCode(OTP)다.
        log.info("[CODEF] 1원 송금 완료 — org={}, account={}, verificationCode={}",
                organization, maskAccountNumber(accountNumber), verificationCode);
    }

    /** 1원 송금 API 호출 + 응답 디코딩(URL-인코딩 바디라 String으로 받아 직접 디코딩). 실패 시 ONE_WON_TRANSFER_FAILED. */
    private CodefBankTransferResponse requestTransfer(String organization, String accountNumber, String verificationCode) {
        CodefBankTransferRequest body = new CodefBankTransferRequest(
                organization, accountNumber, IN_PRINT_TYPE_CUSTOM, verificationCode);

        try {
            String response = codefBankTransferExchange.requestTransfer(body);
            String decoded = URLDecoder.decode(response != null ? response : "", StandardCharsets.UTF_8);
            return OBJECT_MAPPER.readValue(decoded, CodefBankTransferResponse.class);

        } catch (CodefAuthException | RestClientException | IllegalArgumentException | JsonProcessingException e) {
            log.error("[CODEF] 1원 송금 중 오류 — org={}, account={}", organization, maskAccountNumber(accountNumber), e);
            throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }
    }

    /**
     * 로그용 계좌번호 마스킹 (앞 최대 {@value #MAX_VISIBLE_PREFIX_LENGTH}자리 + 뒤 최대
     * {@value #MAX_VISIBLE_SUFFIX_LENGTH}자리만 노출, 중간은 최소 {@value #MIN_MASK_LENGTH}자리를
     * 항상 '*' 로 가린다). 짧은 계좌번호도 전체 노출되지 않도록 길이에 따라 노출 구간을 줄인다.
     */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        int length = accountNumber.length();
        if (length <= MAX_VISIBLE_SUFFIX_LENGTH) {
            return "*".repeat(length);
        }

        int suffixLength = Math.min(MAX_VISIBLE_SUFFIX_LENGTH, length - MIN_MASK_LENGTH);
        int prefixLength = Math.min(
                MAX_VISIBLE_PREFIX_LENGTH,
                length - suffixLength - MIN_MASK_LENGTH
        );
        String prefix = accountNumber.substring(0, prefixLength);
        String suffix = accountNumber.substring(length - suffixLength);
        int maskLength = length - prefixLength - suffixLength;

        return prefix + "*".repeat(maskLength) + suffix;
    }
}
