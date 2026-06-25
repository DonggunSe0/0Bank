package com.team10.backend.domain.codef.auth.ocr;

import com.team10.backend.domain.codef.auth.client.CodefAuthException;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * HTTP 호출 자체는 {@link CodefOcrExchange}로 위임됐다(검증은 {@code CodefHttpServiceConfigTest} 참고).
 * 여기서는 CodefOcrClient의 URL-decode + result.code 판정 + 필드 검증 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CodefOcrClientTest {

    @Mock
    CodefOcrExchange codefOcrExchange;

    @InjectMocks
    CodefOcrClient codefOcrClient;

    @Nested
    @DisplayName("extractIdCard")
    class ExtractIdCard {

        @Test
        @DisplayName("정상 응답 — 이름/주민번호/발급일자 파싱 성공")
        void success() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":{"code":"CF-00000","message":"SUCCESS"},
                     "data":{"resUserName":"홍길동","resUserIdentity":"9012011234560","resIssueDate":"20230115"}}
                    """);

            IdCardOcrResult result = codefOcrClient.extractIdCard(new byte[]{1, 2, 3});

            assertThat(result.name()).isEqualTo("홍길동");
            assertThat(result.residentNumber()).isEqualTo("901201-1234560");
            assertThat(result.issueDate()).isEqualTo("2023-01-15");
        }

        @Test
        @DisplayName("CODEF 실패 코드 응답 → OCR_FAILED")
        void failureCode() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":{"code":"CF-00003","message":"인증 오류"},
                     "data":{"resUserName":"홍길동","resUserIdentity":"9012011234567","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("응답 본문이 JSON null → OCR_FAILED")
        void nullResponseBody() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("null");

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("result 필드 누락 → OCR_FAILED")
        void missingResultField() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"data":{"resUserName":"홍길동","resUserIdentity":"9012011234567","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("data 필드 누락 → OCR_FAILED")
        void missingDataField() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":{"code":"CF-00000","message":"SUCCESS"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("이름 누락 → OCR_FAILED")
        void missingName() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":{"code":"CF-00000"},
                     "data":{"resUserIdentity":"9012011234567","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("주민번호 13자 미만 → OCR_FAILED")
        void identityTooShort() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":{"code":"CF-00000"},
                     "data":{"resUserName":"홍길동","resUserIdentity":"123","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("주민번호 자리수는 맞지만 체크섬이 틀림(운전면허증 등 다른 카드의 숫자열) → OCR_FAILED")
        void identityChecksumInvalid() {
            // 운전면허증의 면허번호 영역이 13자리 숫자+하이픈 형태라 형식 검증만으로는 걸러지지 않는 케이스.
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":{"code":"CF-00000"},
                     "data":{"resUserName":"홍길순","resUserIdentity":"9508292134567","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("발급일자 8자 미만 → OCR_FAILED")
        void issueDateTooShort() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":{"code":"CF-00000"},
                     "data":{"resUserName":"홍길동","resUserIdentity":"9012011234567","resIssueDate":"2023"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("result 필드가 객체가 아닌 다른 타입 → OCR_FAILED (200 OK 응답이 예상과 다른 모양인 케이스)")
        void resultFieldWrongType() {
            when(codefOcrExchange.requestOcr(any())).thenReturn("""
                    {"result":"unexpected-string-value",
                     "data":{"resUserName":"홍길동","resUserIdentity":"9012011234567","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("토큰 응답 파싱 실패(CodefAuthException) → OCR_FAILED로 변환")
        void authFailure() {
            when(codefOcrExchange.requestOcr(any()))
                    .thenThrow(new CodefAuthException("토큰 파싱 실패", new RuntimeException()));

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("토큰 발급 자체가 실패(BusinessException — CodefHttpServiceConfig의 defaultStatusHandler가 변환)하면 OCR_FAILED로 덮어쓰지 않고 그대로 전파한다")
        void tokenIssueFailure_propagatesAsIs() {
            when(codefOcrExchange.requestOcr(any()))
                    .thenThrow(new BusinessException(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED));

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED);
        }

        @Test
        @DisplayName("HTTP 호출 자체 실패 → OCR_FAILED로 변환")
        void httpCallFailure() {
            when(codefOcrExchange.requestOcr(any()))
                    .thenThrow(new RestClientException("connection refused"));

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }
    }
}
