package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefAuthClient;
import com.team10.backend.domain.codef.auth.client.CodefBankTransferExchange;
import com.team10.backend.domain.codef.auth.client.CodefBearerTokenInterceptor;
import com.team10.backend.domain.codef.auth.client.CodefOAuthExchange;
import com.team10.backend.domain.codef.auth.ocr.CodefOcrExchange;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * CODEF 연동(OAuth/OCR/1원송금) 전용 RestClient + 선언적 HTTP 인터페이스 등록.
 * 공유 베이스({@link #codefBaseRestClient()})를 용도별로 mutate해서 baseUrl·Bearer 인터셉터(OAuth는 순환 방지로 제외)·
 * 타임아웃·에러 매핑만 분기한다. OCR/1원송금은 oneWonTransfer 자격증명을 공유(DEMO 재사용 거부 안 됨 확인).
 */
@Configuration(proxyBeanMethods = false)
public class CodefHttpServiceConfig {

    private static final String BASE_URL = "https://development.codef.io"; // 운영 전환 시 api 도메인으로 교체
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration BANK_TRANSFER_READ_TIMEOUT = Duration.ofSeconds(30); // 1원송금은 은행 응답 대기로 30초

    /** OAuth/OCR/1원송금이 공통으로 분기(mutate)하는 베이스 RestClient. baseUrl·인터셉터·에러 매핑은 없다. */
    @Bean
    public RestClient codefBaseRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(DEFAULT_READ_TIMEOUT))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    // ------------------------------------------------------------------
    // OAuth 토큰 발급
    // ------------------------------------------------------------------

    @Bean
    public RestClient codefOAuthRestClient(RestClient codefBaseRestClient) {
        return oauthRestClientBuilder(codefBaseRestClient).build();
    }

    RestClient.Builder oauthRestClientBuilder(RestClient codefBaseRestClient) {
        return codefBaseRestClient.mutate()
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED);
                });
    }

    @Bean
    public CodefOAuthExchange codefOAuthExchange(RestClient codefOAuthRestClient) {
        return proxy(codefOAuthRestClient, CodefOAuthExchange.class);
    }

    // ------------------------------------------------------------------
    // OCR — one-won-transfer 용 자격증명으로 발급된 토큰을 사용 (CodefAuthClientConfig 참고).
    // ------------------------------------------------------------------

    @Bean
    public RestClient codefOcrRestClient(
            RestClient codefBaseRestClient,
            @Qualifier("oneWonTransfer") CodefAuthClient codefAuthClient
    ) {
        return ocrRestClientBuilder(codefBaseRestClient, codefAuthClient).build();
    }

    RestClient.Builder ocrRestClientBuilder(RestClient codefBaseRestClient, CodefAuthClient codefAuthClient) {
        return codefBaseRestClient.mutate()
                .baseUrl(BASE_URL)
                .requestInterceptor(new CodefBearerTokenInterceptor(codefAuthClient))
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(UserErrorCode.OCR_FAILED);
                });
    }

    @Bean
    public CodefOcrExchange codefOcrExchange(RestClient codefOcrRestClient) {
        return proxy(codefOcrRestClient, CodefOcrExchange.class);
    }

    // ------------------------------------------------------------------
    // 1원송금 — one-won-transfer 용 자격증명. 은행 동기 응답 대기로 read timeout만 30초로 override.
    // ------------------------------------------------------------------

    @Bean
    public RestClient codefBankTransferRestClient(
            RestClient codefBaseRestClient,
            @Qualifier("oneWonTransfer") CodefAuthClient codefAuthClient
    ) {
        return bankTransferRestClientBuilder(codefBaseRestClient, codefAuthClient).build();
    }

    RestClient.Builder bankTransferRestClientBuilder(RestClient codefBaseRestClient, CodefAuthClient codefAuthClient) {
        return codefBaseRestClient.mutate()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory(BANK_TRANSFER_READ_TIMEOUT))
                .requestInterceptor(new CodefBearerTokenInterceptor(codefAuthClient))
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED);
                });
    }

    @Bean
    public CodefBankTransferExchange codefBankTransferExchange(RestClient codefBankTransferRestClient) {
        return proxy(codefBankTransferRestClient, CodefBankTransferExchange.class);
    }

    private static <T> T proxy(RestClient restClient, Class<T> exchangeType) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(exchangeType);
    }
}
