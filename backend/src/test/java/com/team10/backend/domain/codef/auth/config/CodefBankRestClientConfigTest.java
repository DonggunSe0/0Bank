package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefAuthClient;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RestClient의 Bearer 토큰 자동 주입과 4xx/5xx 자동 예외 변환을 검증한다.
 * 응답 본문(URL-decode + result.code 판정) 검증은 CodefBankTransferServiceTest 참고.
 */
@ExtendWith(MockitoExtension.class)
class CodefBankRestClientConfigTest {

    private final CodefBankRestClientConfig config = new CodefBankRestClientConfig();

    @Mock
    CodefAuthClient codefAuthClient;

    @Test
    @DisplayName("4xx/5xx 응답을 받으면 try/catch 없이 자동으로 BusinessException(ONE_WON_TRANSFER_FAILED)을 던진다")
    void errorStatus_autoThrowsBusinessException() {
        when(codefAuthClient.getAccessToken()).thenReturn("test-token");

        RestClient.Builder builder = config.restClientBuilder(codefAuthClient);
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

        RestClient.Builder builder = config.restClientBuilder(codefAuthClient);
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
