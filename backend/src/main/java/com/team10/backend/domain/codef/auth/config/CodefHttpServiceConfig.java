package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefOAuthExchange;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * CODEF OAuth 토큰 발급 API 전용 RestClient + 선언적 HTTP 인터페이스 등록.
 * 다른 *HttpServiceConfig와 동일한 패턴(connect/read timeout, defaultStatusHandler)을 적용한다.
 * 단, {@link CodefOAuthExchange#issueToken}의 {@code @PostExchange}가 절대 URL(oauth.codef.io)을
 * 직접 지정하므로 baseUrl은 의미가 없어 설정하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class CodefHttpServiceConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient codefOAuthRestClient() {
        return restClientBuilder().build();
    }

    RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED);
                });
    }

    @Bean
    public CodefOAuthExchange codefOAuthExchange(RestClient codefOAuthRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(codefOAuthRestClient))
                .build();
        return factory.createClient(CodefOAuthExchange.class);
    }
}
