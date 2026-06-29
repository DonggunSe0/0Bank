package com.team10.backend.domain.codef.exAccount.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.team10.backend.domain.codef.exAccount.config.CodefExAccountProperties;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionPayload;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionResult;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountListRequest;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountTransactionListRequest;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationFailure;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static com.team10.backend.domain.codef.exAccount.config.CodefExAccountRestClientConfig.API_REST_CLIENT;

/**
 * CODEF API와 실제 HTTP 통신을 수행하여 계정 등록 및 보유 계좌 정보를 연동하는 RestClient Wrapper 클래스입니다.
 */
@Component
public class CodefExAccountClient {

    private static final int MAX_ATTEMPTS = 2;

    private final CodefExAccountProperties properties;
    private final CodefExAccountAuthClient authClient;
    private final CodefExAccountResponseDecoder responseDecoder;
    private final RestClient restClient;

    public CodefExAccountClient(
            CodefExAccountProperties properties,
            CodefExAccountAuthClient authClient,
            CodefExAccountResponseDecoder responseDecoder,
            @Qualifier(API_REST_CLIENT) RestClient restClient
    ) {
        this.properties = properties;
        this.authClient = authClient;
        this.responseDecoder = responseDecoder;
        this.restClient = restClient;
    }

    /**
     * CODEF API로부터 특정 기관 및 connectedId를 기준으로 보유 계좌 목록 정보를 요청합니다. (실패 시 2회 재시도 정책 적용)
     *
     * @param request CODEF 보유계좌 조회 요청 DTO
     * @return 파싱된 JSON 형태의 응답 Node
     */
    public JsonNode getAccountList(CodefExAccountListRequest request) {
        validateRequest(request);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String responseBody = restClient.post()
                        .uri(properties.accountListPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                return responseDecoder.decodeTransactionData(responseBody);
            } catch (RestClientResponseException exception) {
                if (!shouldRetry(exception, attempt)) {
                    throw new CodefExAccountClientException("CODEF 보유계좌 HTTP 요청에 실패했습니다.", exception);
                }
            } catch (ResourceAccessException exception) {
                if (!canRetry(attempt)) {
                    throw new CodefExAccountClientException("CODEF 보유계좌 요청 시간이 초과되었습니다.", exception);
                }
            }
        }

        throw new CodefExAccountClientException("CODEF 보유계좌 조회에 실패했습니다.");
    }

    /**
     * CODEF API로부터 특정 계좌의 거래내역을 요청합니다.
     */
    public JsonNode getTransactionList(CodefExAccountTransactionListRequest request) {
        validateTransactionRequest(request);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String responseBody = restClient.post()
                        .uri(properties.bankTransactionPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                return responseDecoder.decodeData(responseBody);
            } catch (RestClientResponseException exception) {
                if (!shouldRetry(exception, attempt)) {
                    throw new CodefExAccountClientException("CODEF 거래내역 HTTP 요청에 실패했습니다.", exception);
                }
            } catch (ResourceAccessException exception) {
                if (!canRetry(attempt)) {
                    throw new CodefExAccountClientException("CODEF 거래내역 요청 시간이 초과되었습니다.", exception);
                }
            }
        }

        throw new CodefExAccountClientException("CODEF 거래내역 조회에 실패했습니다.");
    }

    /**
     * CODEF API에 금융기관 계정 인증 정보를 전송하여 새로운 계정을 생성(기관 연결)하고, 결과를 반환합니다.
     *
     * @param payload CODEF 계정생성 요청 페이로드
     * @return 발급된 connectedId가 포함된 등록 결과 DTO
     */
    public CodefExAccountConnectionResult createConnection(
            CodefExAccountConnectionPayload payload
    ) {
        if (payload == null || payload.accountList().isEmpty()) {
            throw new CodefExAccountRegistrationException(
                    CodefExAccountRegistrationFailure.INVALID_RESPONSE,
                    "CODEF 계정등록 요청값이 올바르지 않습니다."
            );
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String responseBody = restClient.post()
                        .uri(properties.accountCreatePath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(String.class);
                return responseDecoder.decodeConnectionResult(responseBody);
            } catch (CodefExAccountRegistrationException exception) {
                throw exception;
            } catch (RestClientResponseException exception) {
                if (!shouldRetry(exception, attempt)) {
                    throw registrationSystemError("CODEF 계정등록 HTTP 요청에 실패했습니다.", exception);
                }
            } catch (ResourceAccessException exception) {
                if (!canRetry(attempt)) {
                    throw registrationSystemError("CODEF 계정등록 요청 시간이 초과되었습니다.", exception);
                }
            } catch (CodefExAccountClientException exception) {
                throw new CodefExAccountRegistrationException(
                        CodefExAccountRegistrationFailure.INVALID_RESPONSE,
                        "CODEF 계정등록 응답이 올바르지 않습니다.",
                        exception
                );
            }
        }

        throw registrationSystemError("CODEF 계정등록에 실패했습니다.", null);
    }

    /**
     * 계좌 목록 조회 요청의 필수 인자들을 검증합니다.
     */
    private void validateRequest(CodefExAccountListRequest request) {
        if (request == null
                || request.organization() == null
                || request.organization().isBlank()
                || request.connectedId() == null
                || request.connectedId().isBlank()) {
            throw new CodefExAccountClientException("CODEF 보유계좌 요청값이 올바르지 않습니다.");
        }
    }

    private void validateTransactionRequest(CodefExAccountTransactionListRequest request) {
        if (request == null
                || request.organization() == null
                || request.organization().isBlank()
                || request.connectedId() == null
                || request.connectedId().isBlank()
                || request.account() == null
                || request.account().isBlank()) {
            throw new CodefExAccountClientException("CODEF 거래내역 요청값이 올바르지 않습니다.");
        }
    }

    private boolean shouldRetry(RestClientResponseException exception, int attempt) {
        return exception.getStatusCode().is5xxServerError() && canRetry(attempt);
    }

    private boolean canRetry(int attempt) {
        return attempt < MAX_ATTEMPTS;
    }

    /**
     * CODEF API 통신 중 발생한 오류를 비즈니스 예외 타입으로 래핑합니다.
     */
    private CodefExAccountRegistrationException registrationSystemError(
            String message,
            Throwable cause
    ) {
        return new CodefExAccountRegistrationException(
                CodefExAccountRegistrationFailure.SYSTEM_ERROR,
                message,
                cause
        );
    }
}
