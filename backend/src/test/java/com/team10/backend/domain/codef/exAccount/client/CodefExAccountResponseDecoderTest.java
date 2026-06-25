package com.team10.backend.domain.codef.exAccount.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionResult;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationFailure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class CodefExAccountResponseDecoderTest {

    private final CodefExAccountResponseDecoder decoder =
            new CodefExAccountResponseDecoder(new ObjectMapper());

    @Test
    void rejectsCodefFailureWithoutExposingUpstreamMessage() {
        String response = """
                {
                  "result": {
                    "code": "CF-94002",
                    "message": "민감한 기관 원문 메시지"
                  },
                  "data": {}
                }
                """;

        assertThatThrownBy(() -> decoder.decodeData(response))
                .isInstanceOf(CodefExAccountClientException.class)
                .hasMessage("CODEF 보유계좌 조회에 실패했습니다.")
                .hasMessageNotContaining("민감한 기관 원문 메시지");
    }

    @Test
    void rejectsMalformedResponse() {
        assertThatThrownBy(() -> decoder.decodeData("not-json"))
                .isInstanceOf(CodefExAccountClientException.class)
                .hasMessage("CODEF 보유계좌 응답을 해석할 수 없습니다.");
    }

    @Test
    void decodesSuccessfulAccountRegistration() {
        CodefExAccountConnectionResult result = decoder.decodeConnectionResult("""
                {
                  "result": {"code": "CF-00000"},
                  "data": {
                    "successList": [{"code": "CF-00000", "organization": "0004"}],
                    "errorList": [],
                    "connectedId": "issued-connected-id"
                  }
                }
                """);

        assertThat(result.connectedId()).isEqualTo("issued-connected-id");
    }

    @Test
    void classifiesCredentialFailure() {
        assertRegistrationFailure("""
                {
                  "result": {"code": "CF-94002", "message": "기관 원문 메시지"},
                  "data": {"errorList": [{"code": "CF-94002"}]}
                }
                """, CodefExAccountRegistrationFailure.CREDENTIAL_INVALID, "은행 인증정보가 올바르지 않습니다.");
    }

    @Test
    void classifiesAccountErrorListAsCredentialFailure() {
        assertRegistrationFailure("""
                {
                  "result": {"code": "CF-00000"},
                  "data": {
                    "successList": [],
                    "errorList": [{"code": "CF-01004", "message": "기관 인증 실패"}]
                  }
                }
                """, CodefExAccountRegistrationFailure.CREDENTIAL_INVALID,
                "은행 인증정보가 올바르지 않습니다.");
    }

    @Test
    void classifiesAdditionalAuthentication() {
        assertRegistrationFailure("""
                {
                  "result": {"code": "CF-03002"},
                  "data": {"continue2Way": true}
                }
                """, CodefExAccountRegistrationFailure.ADDITIONAL_AUTH_REQUIRED,
                "CODEF 계정등록에 추가인증이 필요합니다.");
    }

    @Test
    void classifiesUnknownCodeAsSystemError() {
        assertRegistrationFailure("""
                {
                  "result": {"code": "CF-99999"},
                  "data": {}
                }
                """, CodefExAccountRegistrationFailure.SYSTEM_ERROR,
                "CODEF 계정등록 처리 중 외부 시스템 오류가 발생했습니다.");
    }

    @Test
    void rejectsSuccessWithoutConnectedId() {
        assertRegistrationFailure("""
                {
                  "result": {"code": "CF-00000"},
                  "data": {
                    "successList": [{"code": "CF-00000"}],
                    "connectedId": ""
                  }
                }
                """, CodefExAccountRegistrationFailure.INVALID_RESPONSE,
                "CODEF 계정등록 응답이 올바르지 않습니다.");
    }

    private void assertRegistrationFailure(
            String response,
            CodefExAccountRegistrationFailure expectedFailure,
            String expectedMessage
    ) {
        assertThatThrownBy(() -> decoder.decodeConnectionResult(response))
                .isInstanceOfSatisfying(CodefExAccountRegistrationException.class, exception -> {
                    assertThat(exception.getFailure()).isEqualTo(expectedFailure);
                    assertThat(exception.getMessage()).isEqualTo(expectedMessage);
                    assertThat(exception.getMessage()).doesNotContain("기관 원문 메시지");
                });
    }
}
