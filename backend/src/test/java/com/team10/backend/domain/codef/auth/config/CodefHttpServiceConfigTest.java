package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * RestClient의 4xx/5xx 자동 예외 변환을 검증한다.
 * CodefOAuthExchange.issueToken()의 @PostExchange가 절대 URL(oauth.codef.io)을 직접 지정하므로
 * baseUrl/Bearer 헤더 주입은 다른 *HttpServiceConfigTest와 달리 검증 대상이 아니다.
 */
class CodefHttpServiceConfigTest {

    private final CodefHttpServiceConfig config = new CodefHttpServiceConfig();

    @Test
    @DisplayName("4xx/5xx 응답을 받으면 try/catch 없이 자동으로 BusinessException(CODEF_TOKEN_ISSUE_FAILED)을 던진다")
    void errorStatus_autoThrowsBusinessException() {
        RestClient.Builder builder = config.restClientBuilder();
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
}
