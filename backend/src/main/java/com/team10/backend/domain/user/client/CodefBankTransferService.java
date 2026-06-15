package com.team10.backend.domain.user.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.verification.BankTransferService;
import com.team10.backend.global.exception.BusinessException;
import io.codef.api.EasyCodef;
import io.codef.api.EasyCodefServiceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

// NOTE: easycodef-java 라이브러리가 Jackson 2.x(com.fasterxml)에 의존하므로
//       응답 파싱도 동일 버전의 ObjectMapper를 로컬 인스턴스로 사용한다.
//       Spring Boot 4.x가 자동 등록하는 ObjectMapper는 Jackson 3.x(tools.jackson)라 주입 불가.

/**
 * CODEF API 기반 1원 계좌인증 서비스.
 *
 * <h2>API 스펙</h2>
 * <pre>
 * POST /v1/kr/bank/a/account/transfer-authentication
 *
 * Request:
 *   organization    : 은행 기관코드 (필수)
 *   account         : 계좌번호, '-' 없이 숫자만 (필수)
 *   inPrintType     : "9" — 고객사 직접 입력
 *   inPrintContent  : 입금자명에 넣을 인증코드 (직접 지정)
 *
 * Response:
 *   data.authCode   : 인증코드 (inPrintType=9이면 inPrintContent 그대로 반환)
 * </pre>
 *
 * <p>{@code @Primary}로 {@link com.team10.backend.domain.user.verification.MockBankTransferService} 대신 자동 주입된다.
 * 운영 전환 시 {@link EasyCodefServiceType#API}로 변경하고 정식 클라이언트 정보로 교체한다.
 */
@Slf4j
@Service
@Primary
public class CodefBankTransferService implements BankTransferService {

    private static final String TRANSFER_AUTH_URL = "/v1/kr/bank/a/account/transfer-authentication";
    /** inPrintType=9: 고객사 직접 입력 — inPrintContent에 지정한 코드를 입금자명으로 사용 */
    private static final String IN_PRINT_TYPE_CUSTOM = "9";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EasyCodef easyCodef;

    public CodefBankTransferService(
            @Value("${codef.client-id}") String clientId,
            @Value("${codef.client-secret}") String clientSecret,
            @Value("${codef.public-key}") String publicKey
    ) {
        this.easyCodef = new EasyCodef();
        easyCodef.setClientInfoForDemo(clientId, clientSecret);
        easyCodef.setPublicKey(publicKey);
    }

    /**
     * CODEF를 통해 사용자 계좌로 1원을 송금한다.
     * 입금자명에 {@code verificationCode}를 포함시켜 사용자가 확인할 수 있게 한다.
     *
     * @param organization     CODEF 은행 기관코드 (예: 004=국민, 020=우리, 081=하나)
     * @param accountNumber    수신 계좌번호 ('-' 없이 숫자만)
     * @param verificationCode 입금자명에 포함할 4자리 인증코드
     * @throws BusinessException 송금 실패 또는 API 오류 시
     */
    @Override
    public void sendOneWon(String organization, String accountNumber, String verificationCode) {
        try {
            HashMap<String, Object> params = new HashMap<>();
            params.put("organization", organization);
            params.put("account", accountNumber);
            params.put("inPrintType", IN_PRINT_TYPE_CUSTOM);
            params.put("inPrintContent", verificationCode);

            String response = easyCodef.requestProduct(
                    TRANSFER_AUTH_URL,
                    EasyCodefServiceType.DEMO,
                    params
            );

            log.debug("[CODEF] 1원 이체 응답 — {}", response);

            Map<?, ?> responseMap = OBJECT_MAPPER.readValue(response, Map.class);
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
