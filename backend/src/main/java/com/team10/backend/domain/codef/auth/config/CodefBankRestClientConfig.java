package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefAuthClient;
import com.team10.backend.domain.codef.auth.client.CodefBankTransferExchange;
import com.team10.backend.domain.codef.auth.client.CodefBearerTokenInterceptor;
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

// 1원 송금 전용 RestClient — 30초 read timeout(은행 동기 응답 대기), Bearer 토큰 자동 주입,
// 4xx/5xx 자동 예외 변환.
@Configuration(proxyBeanMethods = false)
public class CodefBankRestClientConfig {

    private static final String BASE_URL = "https://development.codef.io"; // 운영 전환 시 api 도메인으로 교체

    // one-won-transfer 용 자격증명으로 발급된 토큰을 사용 (CodefAuthClientConfig 참고).
    @Bean
    public RestClient codefBankTransferRestClient(@Qualifier("oneWonTransfer") CodefAuthClient codefAuthClient) {
        return restClientBuilder(codefAuthClient).build();
    }

    RestClient.Builder restClientBuilder(CodefAuthClient codefAuthClient) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30)); // 1원 송금은 은행 응답 대기로 5초를 초과하는 경우가 있어 30초로 설정

        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .requestInterceptor(new CodefBearerTokenInterceptor(codefAuthClient))
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
                });
    }

    @Bean
    public CodefBankTransferExchange codefBankTransferExchange(RestClient codefBankTransferRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(codefBankTransferRestClient))
                .build();
        return factory.createClient(CodefBankTransferExchange.class);
    }
}
