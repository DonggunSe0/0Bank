package com.team10.backend.domain.codef.auth.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * CODEF API 호출마다 {@link CodefAuthClient}의 현재 토큰으로 Authorization 헤더를 채우는 인터셉터.
 * CodefAuthClient는 용도별 빈이라 RestClient 빈마다 따로 생성하며, 토큰 발급 실패 시 예외를 그대로 전파한다.
 */
public class CodefBearerTokenInterceptor implements ClientHttpRequestInterceptor {

    private final CodefAuthClient codefAuthClient;

    public CodefBearerTokenInterceptor(CodefAuthClient codefAuthClient) {
        this.codefAuthClient = codefAuthClient;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution
    ) throws IOException {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + codefAuthClient.getAccessToken());
        return execution.execute(request, body);
    }
}
