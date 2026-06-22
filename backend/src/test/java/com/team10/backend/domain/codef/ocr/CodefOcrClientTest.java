package com.team10.backend.domain.codef.ocr;

import com.team10.backend.domain.codef.client.CodefAuthClient;
import com.team10.backend.domain.codef.client.CodefAuthException;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodefOcrClientTest {

    @Mock CodefAuthClient codefAuthClient;
    @Mock RestClient restClient;

    @InjectMocks
    CodefOcrClient codefOcrClient;

    private void mockHttpResponse(String body) {
        when(codefAuthClient.getAccessToken()).thenReturn("test-token");

        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(body);
    }

    private void mockHttpFailure(RuntimeException ex) {
        when(codefAuthClient.getAccessToken()).thenReturn("test-token");

        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(ex);
    }

    @Nested
    @DisplayName("extractIdCard")
    class ExtractIdCard {

        @Test
        @DisplayName("정상 응답 — 이름/주민번호/발급일자 파싱 성공")
        void success() {
            mockHttpResponse("""
                    {"result":{"code":"CF-00000","message":"SUCCESS"},
                     "data":{"resUserName":"홍길동","resUserIdentity":"9012011234567","resIssueDate":"20230115"}}
                    """);

            IdCardOcrResult result = codefOcrClient.extractIdCard(new byte[]{1, 2, 3});

            assertThat(result.name()).isEqualTo("홍길동");
            assertThat(result.residentNumber()).isEqualTo("901201-1234567");
            assertThat(result.issueDate()).isEqualTo("2023-01-15");
        }

        @Test
        @DisplayName("CODEF 실패 코드 응답 → OCR_FAILED")
        void failureCode() {
            mockHttpResponse("""
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
            mockHttpResponse("null");

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("result 필드 누락 → OCR_FAILED")
        void missingResultField() {
            mockHttpResponse("""
                    {"data":{"resUserName":"홍길동","resUserIdentity":"9012011234567","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("data 필드 누락 → OCR_FAILED")
        void missingDataField() {
            mockHttpResponse("""
                    {"result":{"code":"CF-00000","message":"SUCCESS"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("이름 누락 → OCR_FAILED")
        void missingName() {
            mockHttpResponse("""
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
            mockHttpResponse("""
                    {"result":{"code":"CF-00000"},
                     "data":{"resUserName":"홍길동","resUserIdentity":"123","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("발급일자 8자 미만 → OCR_FAILED")
        void issueDateTooShort() {
            mockHttpResponse("""
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
            mockHttpResponse("""
                    {"result":"unexpected-string-value",
                     "data":{"resUserName":"홍길동","resUserIdentity":"9012011234567","resIssueDate":"20230115"}}
                    """);

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("토큰 발급 실패 → OCR_FAILED로 변환")
        void authFailure() {
            when(codefAuthClient.getAccessToken())
                    .thenThrow(new CodefAuthException("토큰 파싱 실패", new RuntimeException()));

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("HTTP 호출 자체 실패 → OCR_FAILED로 변환")
        void httpCallFailure() {
            mockHttpFailure(new RestClientException("connection refused"));

            assertThatThrownBy(() -> codefOcrClient.extractIdCard(new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }
    }
}
