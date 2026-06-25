package com.team10.backend.domain.user.config;

import com.team10.backend.domain.user.client.PortOneIdentityVerificationExchange;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/** 포트원(PortOne) API 전용 RestClient + 선언적 HTTP 인터페이스 등록. */
@Configuration(proxyBeanMethods = false)
public class PortOneHttpServiceConfig {

    public static final String PORTONE_REST_CLIENT = "portOneRestClient";

    private static final String BASE_URL = "https://api.portone.io";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean(name = PORTONE_REST_CLIENT)
    public RestClient portOneRestClient(@Value("${portone.api-secret}") String apiSecret) {
        return restClientBuilder(apiSecret).build();
    }

    RestClient.Builder restClientBuilder(String apiSecret) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .requestFactory(requestFactory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
                });
    }

    @Bean
    public PortOneIdentityVerificationExchange portOneIdentityVerificationExchange(
            @Qualifier(PORTONE_REST_CLIENT) RestClient portOneRestClient
    ) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(portOneRestClient))
                .build();
        return factory.createClient(PortOneIdentityVerificationExchange.class);
    }
}
