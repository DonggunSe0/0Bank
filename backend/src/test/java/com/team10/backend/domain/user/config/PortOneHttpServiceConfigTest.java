package com.team10.backend.domain.user.config;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * PortOneHttpServiceConfig가 만드는 RestClient의 핵심 동작(기본 헤더, 4xx/5xx 자동 예외 변환)을
 * try/catch 없이도 보장하는지 검증한다. 실제 PortOneIdentityVerificationExchange 호출부 테스트는
 * PortOneClientTest 참고.
 */
class PortOneHttpServiceConfigTest {

    private final PortOneHttpServiceConfig config = new PortOneHttpServiceConfig();

    @Test
    @DisplayName("4xx/5xx 응답을 받으면 try/catch 없이 자동으로 BusinessException(IDENTITY_VERIFICATION_FAILED)을 던진다")
    void errorStatus_autoThrowsBusinessException() {
        RestClient.Builder builder = config.restClientBuilder("test-secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.portone.io/identity-verifications/bad-id"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> restClient.get()
                .uri("/identity-verifications/{id}", "bad-id")
                .retrieve()
                .body(String.class))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("모든 요청에 Authorization: PortOne {apiSecret} 기본 헤더가 자동으로 붙는다")
    void defaultHeader_includesApiSecret() {
        RestClient.Builder builder = config.restClientBuilder("test-secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.portone.io/identity-verifications/ok-id"))
                .andExpect(header("Authorization", "PortOne test-secret"))
                .andRespond(withSuccess("{\"status\":\"VERIFIED\"}", MediaType.APPLICATION_JSON));

        String body = restClient.get()
                .uri("/identity-verifications/{id}", "ok-id")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("VERIFIED");
    }
}
