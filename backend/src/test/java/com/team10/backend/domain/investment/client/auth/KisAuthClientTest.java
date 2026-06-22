package com.team10.backend.domain.investment.client.auth;

import static com.team10.backend.domain.investment.config.KisConstants.Auth.TOKEN_ISSUE_PATH;
import static com.team10.backend.domain.investment.config.KisConstants.Auth.TOKEN_REVOKE_PATH;
import static com.team10.backend.domain.investment.config.KisConstants.Auth.WEBSOCKET_APPROVAL_PATH;
import static com.team10.backend.domain.investment.config.KisConstants.BASE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.team10.backend.domain.investment.client.auth.dto.KisAccessToken;
import com.team10.backend.domain.investment.client.auth.dto.KisWebSocketApprovalKey;
import com.team10.backend.domain.investment.config.KisProperties;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KisAuthClientTest {

    private MockRestServiceServer server;
    private KisAuthClient kisAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build(); /** RestClient가 생성한 요청을 가로채 테스트한다 */
        kisAuthClient = new KisAuthClient(builder.build(), new KisProperties("app-key", "app-secret", 41));
    }

    @Test
    @DisplayName("AccessToken 발급 API를 호출하고 응답 만료 시간을 파싱한다")
    void issueAccessToken() {
        server.expect(requestTo(BASE_URL + TOKEN_ISSUE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andExpect(jsonPath("$.appkey").value("app-key"))
                .andExpect(jsonPath("$.appsecret").value("app-secret"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "issued-token",
                          "access_token_token_expired": "2026-06-16 15:30:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        KisAccessToken result = kisAuthClient.issueAccessToken();

        assertThat(result.accessToken()).isEqualTo("issued-token");
        assertThat(result.expiresAt()).isEqualTo(LocalDateTime.of(2026, 6, 16, 15, 30));
        server.verify();
    }

    @Test
    @DisplayName("AccessToken 발급 응답에 토큰이 없으면 인증 실패 예외를 던진다")
    void issueAccessTokenFailsWhenTokenIsMissing() {
        server.expect(requestTo(BASE_URL + TOKEN_ISSUE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kisAuthClient.issueAccessToken())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.KIS_AUTH_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("웹소켓 접속키 발급 API를 호출하고 approval_key를 파싱한다")
    void issueWebSocketApprovalKey() {
        server.expect(requestTo(BASE_URL + WEBSOCKET_APPROVAL_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andExpect(jsonPath("$.appkey").value("app-key"))
                .andExpect(jsonPath("$.secretkey").value("app-secret"))
                .andRespond(withSuccess("""
                        {
                          "approval_key": "approval-key"
                        }
                        """, MediaType.APPLICATION_JSON));

        KisWebSocketApprovalKey result = kisAuthClient.issueWebSocketApprovalKey();

        assertThat(result.approvalKey()).isEqualTo("approval-key");
        server.verify();
    }

    @Test
    @DisplayName("웹소켓 접속키 발급 응답에 접속키가 없으면 인증 실패 예외를 던진다")
    void issueWebSocketApprovalKeyFailsWhenApprovalKeyIsMissing() {
        server.expect(requestTo(BASE_URL + WEBSOCKET_APPROVAL_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kisAuthClient.issueWebSocketApprovalKey())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.KIS_AUTH_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("AccessToken 폐기 API에 발급된 토큰을 전달한다")
    void revokeAccessToken() {
        server.expect(requestTo(BASE_URL + TOKEN_REVOKE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.appkey").value("app-key"))
                .andExpect(jsonPath("$.appsecret").value("app-secret"))
                .andExpect(jsonPath("$.token").value("access-token"))
                .andRespond(withSuccess());

        kisAuthClient.revokeAccessToken("access-token");

        server.verify();
    }
}
