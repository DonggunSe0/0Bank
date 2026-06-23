package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefAuthClient;
import com.team10.backend.domain.codef.auth.client.CodefBearerTokenInterceptor;
import com.team10.backend.domain.codef.auth.ocr.CodefOcrExchange;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/** CODEF OCR API 전용 RestClient + 선언적 HTTP 인터페이스 등록. */
@Configuration(proxyBeanMethods = false)
public class CodefOcrHttpServiceConfig {

    private static final String BASE_URL = "https://development.codef.io"; // 운영 전환 시 api 도메인으로 교체
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    // account-inquiry 용 자격증명으로 발급된 토큰을 사용 (CodefAuthClientConfig 참고).
    @Bean
    public RestClient codefOcrRestClient(@Qualifier("accountInquiry") CodefAuthClient codefAuthClient) {
        return restClientBuilder(codefAuthClient).build();
    }

    RestClient.Builder restClientBuilder(CodefAuthClient codefAuthClient) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .requestInterceptor(new CodefBearerTokenInterceptor(codefAuthClient))
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(UserErrorCode.OCR_FAILED);
                });
    }

    @Bean
    public CodefOcrExchange codefOcrExchange(RestClient codefOcrRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(codefOcrRestClient))
                .build();
        return factory.createClient(CodefOcrExchange.class);
    }
}
