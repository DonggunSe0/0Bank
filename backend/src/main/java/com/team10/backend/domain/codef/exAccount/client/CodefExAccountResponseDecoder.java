package com.team10.backend.domain.codef.exAccount.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionResult;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationFailure;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class CodefExAccountResponseDecoder {

    private static final String SUCCESS_CODE = "CF-00000";
    private static final String ADDITIONAL_AUTH_CODE = "CF-03002";
    private static final String CREDENTIAL_INVALID_CODE = "CF-94002";

    private final ObjectMapper objectMapper;

    public CodefExAccountResponseDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode decodeData(String responseBody) {
        return decodeData(responseBody, "CODEF 보유계좌");
    }

    public JsonNode decodeTransactionData(String responseBody) {
        return decodeData(responseBody, "CODEF 거래내역");
    }

    private JsonNode decodeData(String responseBody, String operation) {
        JsonNode root = parseResponse(responseBody, operation);
        String resultCode = root.path("result").path("code").asText();
        JsonNode data = root.path("data");
        if (!SUCCESS_CODE.equals(resultCode) || data.isMissingNode() || data.isNull()) {
            throw new CodefExAccountClientException(operation + " 조회에 실패했습니다.");
        }
        return data;
    }

    public CodefExAccountConnectionResult decodeConnectionResult(String responseBody) {
        JsonNode root = parseResponse(responseBody, "CODEF 계정등록");
        String resultCode = root.path("result").path("code").asText();
        JsonNode data = root.path("data");

        if (ADDITIONAL_AUTH_CODE.equals(resultCode) && data.path("continue2Way").asBoolean(false)) {
            throw registrationFailure(
                    CodefExAccountRegistrationFailure.ADDITIONAL_AUTH_REQUIRED,
                    "CODEF 계정등록에 추가인증이 필요합니다."
            );
        }
        if (CREDENTIAL_INVALID_CODE.equals(resultCode)
                || (SUCCESS_CODE.equals(resultCode) && hasEntries(data.path("errorList")))) {
            throw registrationFailure(
                    CodefExAccountRegistrationFailure.CREDENTIAL_INVALID,
                    "은행 인증정보가 올바르지 않습니다."
            );
        }
        if (!SUCCESS_CODE.equals(resultCode)) {
            throw registrationFailure(
                    CodefExAccountRegistrationFailure.SYSTEM_ERROR,
                    "CODEF 계정등록 처리 중 외부 시스템 오류가 발생했습니다."
            );
        }

        String connectedId = data.path("connectedId").asText();
        if (connectedId.isBlank() || !containsCode(data.path("successList"), SUCCESS_CODE)) {
            throw registrationFailure(
                    CodefExAccountRegistrationFailure.INVALID_RESPONSE,
                    "CODEF 계정등록 응답이 올바르지 않습니다."
            );
        }
        return new CodefExAccountConnectionResult(connectedId);
    }

    private JsonNode parseResponse(String responseBody, String operation) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new CodefExAccountClientException(operation + " 응답이 비어 있습니다.");
        }

        try {
            String decodedBody = URLDecoder.decode(responseBody, StandardCharsets.UTF_8);
            return objectMapper.readTree(decodedBody);
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw new CodefExAccountClientException(operation + " 응답을 해석할 수 없습니다.", exception);
        }
    }

    private boolean containsCode(JsonNode entries, String expectedCode) {
        if (!entries.isArray()) {
            return false;
        }
        for (JsonNode entry : entries) {
            if (expectedCode.equals(entry.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEntries(JsonNode entries) {
        return entries.isArray() && !entries.isEmpty();
    }

    private CodefExAccountRegistrationException registrationFailure(
            CodefExAccountRegistrationFailure failure,
            String message
    ) {
        return new CodefExAccountRegistrationException(failure, message);
    }
}
