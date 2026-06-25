package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefAuthClient;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RestClient의 4xx/5xx 자동 예외 변환과 Bearer 토큰 자동 주입을 검증한다.
 * OAuth/OCR/1원송금이 공유 베이스 RestClient({@code codefBaseRestClient})에서 {@code mutate()}로
 * 분기되므로 목적별로 {@code @Nested}로 나눠 검증한다.
 * 응답 본문(URL-decode + result.code 판정) 검증은 CodefOcrClientTest/CodefBankTransferServiceTest 참고.
 * (구 CodefBankRestClientConfigTest/CodefOcrHttpServiceConfigTest를 이 파일로 통합함)
 */
@ExtendWith(MockitoExtension.class)
class CodefHttpServiceConfigTest {

    private final CodefHttpServiceConfig config = new CodefHttpServiceConfig();

    @Mock
    CodefAuthClient codefAuthClient;

    @Nested
    @DisplayName("OAuth 토큰 발급")
    class OAuth {

        @Test
        @DisplayName("4xx/5xx 응답을 받으면 try/catch 없이 자동으로 BusinessException(CODEF_TOKEN_ISSUE_FAILED)을 던진다")
        void errorStatus_autoThrowsBusinessException() {
            RestClient.Builder builder = config.oauthRestClientBuilder(config.codefBaseRestClient());
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            server.expect(requestTo("https://oauth.codef.io/oauth/token"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> restClient.post()
                    .uri("https://oauth.codef.io/oauth/token")
                    .retrieve()
                    .body(String.class))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED);
        }

        @Test
        @DisplayName("Bearer 인터셉터가 붙어있지 않다 — 토큰 발급 호출 자체에 토큰을 요구하면 순환이 되므로 절대 붙이지 않는다")
        void doesNotInjectBearerToken() {
            RestClient.Builder builder = config.oauthRestClientBuilder(config.codefBaseRestClient());
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            server.expect(requestTo("https://oauth.codef.io/oauth/token"))
                    .andExpect(headerDoesNotExist("Authorization"))
                    .andRespond(withSuccess("{\"access_token\":\"x\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

            restClient.post().uri("https://oauth.codef.io/oauth/token").retrieve().body(String.class);
        }
    }

    @Nested
    @DisplayName("OCR")
    class Ocr {

        @Test
        @DisplayName("4xx/5xx 응답을 받으면 try/catch 없이 자동으로 BusinessException(OCR_FAILED)을 던진다")
        void errorStatus_autoThrowsBusinessException() {
            when(codefAuthClient.getAccessToken()).thenReturn("test-token");

            RestClient.Builder builder = config.ocrRestClientBuilder(config.codefBaseRestClient(), codefAuthClient);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            server.expect(requestTo("https://development.codef.io/v1/kr/etc/a/ocr/registration-card"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThatThrownBy(() -> restClient.post()
                    .uri("/v1/kr/etc/a/ocr/registration-card")
                    .retrieve()
                    .body(String.class))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_FAILED);
        }

        @Test
        @DisplayName("모든 요청에 Authorization: Bearer {token}이 CodefAuthClient로부터 자동으로 붙는다")
        void requestInterceptor_injectsBearerTokenFromAuthClient() {
            when(codefAuthClient.getAccessToken()).thenReturn("ocr-access-token");

            RestClient.Builder builder = config.ocrRestClientBuilder(config.codefBaseRestClient(), codefAuthClient);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            server.expect(requestTo("https://development.codef.io/v1/kr/etc/a/ocr/registration-card"))
                    .andExpect(header("Authorization", "Bearer ocr-access-token"))
                    .andRespond(withSuccess("{\"result\":{\"code\":\"CF-00000\"}}", MediaType.APPLICATION_JSON));

            String body = restClient.post()
                    .uri("/v1/kr/etc/a/ocr/registration-card")
                    .retrieve()
                    .body(String.class);

            assertThat(body).contains("CF-00000");
        }
    }

    @Nested
    @DisplayName("1원송금")
    class BankTransfer {

        @Test
        @DisplayName("4xx/5xx 응답을 받으면 try/catch 없이 자동으로 BusinessException(ONE_WON_TRANSFER_FAILED)을 던진다")
        void errorStatus_autoThrowsBusinessException() {
            when(codefAuthClient.getAccessToken()).thenReturn("test-token");

            RestClient.Builder builder = config.bankTransferRestClientBuilder(config.codefBaseRestClient(), codefAuthClient);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            server.expect(requestTo("https://development.codef.io/v1/kr/bank/a/account/transfer-authentication"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThatThrownBy(() -> restClient.post()
                    .uri("/v1/kr/bank/a/account/transfer-authentication")
                    .retrieve()
                    .body(String.class))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        @Test
        @DisplayName("모든 요청에 Authorization: Bearer {token}이 CodefAuthClient로부터 자동으로 붙는다")
        void requestInterceptor_injectsBearerTokenFromAuthClient() {
            when(codefAuthClient.getAccessToken()).thenReturn("transfer-access-token");

            RestClient.Builder builder = config.bankTransferRestClientBuilder(config.codefBaseRestClient(), codefAuthClient);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            server.expect(requestTo("https://development.codef.io/v1/kr/bank/a/account/transfer-authentication"))
                    .andExpect(header("Authorization", "Bearer transfer-access-token"))
                    .andRespond(withSuccess("{\"result\":{\"code\":\"CF-00000\"}}", MediaType.APPLICATION_JSON));

            String body = restClient.post()
                    .uri("/v1/kr/bank/a/account/transfer-authentication")
                    .retrieve()
                    .body(String.class);

            assertThat(body).contains("CF-00000");
        }
    }
}
