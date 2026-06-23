package com.team10.backend.domain.codef.exAccount.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CodefExAccountRestClientConfig {

    public static final String OAUTH_REST_CLIENT = "codefExAccountOAuthRestClient";
    public static final String API_REST_CLIENT = "codefExAccountApiRestClient";

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int OAUTH_READ_TIMEOUT_MILLIS = 5_000;
    private static final int API_READ_TIMEOUT_MILLIS = 30_000;

    @Bean(name = OAUTH_REST_CLIENT)
    public RestClient codefExAccountOAuthRestClient() {
        return createRestClient(OAUTH_READ_TIMEOUT_MILLIS);
    }

    @Bean(name = API_REST_CLIENT)
    public RestClient codefExAccountApiRestClient(CodefExAccountProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(createRequestFactory(API_READ_TIMEOUT_MILLIS))
                .build();
    }

    private RestClient createRestClient(int readTimeoutMillis) {
        return RestClient.builder()
                .requestFactory(createRequestFactory(readTimeoutMillis))
                .build();
    }

    private SimpleClientHttpRequestFactory createRequestFactory(int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout(readTimeoutMillis);
        return factory;
    }
}
