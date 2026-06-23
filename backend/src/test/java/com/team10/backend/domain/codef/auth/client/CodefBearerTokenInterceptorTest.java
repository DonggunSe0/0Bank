package com.team10.backend.domain.codef.auth.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CodefBearerTokenInterceptor}가 매 요청마다 {@link CodefAuthClient}의 현재 토큰으로
 * Authorization 헤더를 채우고, 실제 전송은 그대로 {@link ClientHttpRequestExecution}에 위임하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CodefBearerTokenInterceptorTest {

    @Mock
    CodefAuthClient codefAuthClient;

    @Mock
    ClientHttpRequestExecution execution;

    @Mock
    ClientHttpResponse expectedResponse;

    @Test
    @DisplayName("Authorization: Bearer {token} 헤더를 설정하고 execution에 위임한다")
    void intercept_setsBearerHeaderAndDelegates() throws IOException {
        when(codefAuthClient.getAccessToken()).thenReturn("test-access-token");
        when(execution.execute(any(), any())).thenReturn(expectedResponse);

        HttpRequest request = new MockClientHttpRequest();
        byte[] body = "payload".getBytes();

        CodefBearerTokenInterceptor interceptor = new CodefBearerTokenInterceptor(codefAuthClient);
        ClientHttpResponse actualResponse = interceptor.intercept(request, body, execution);

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-access-token");
        assertThat(actualResponse).isSameAs(expectedResponse);
        verify(execution).execute(request, body);
    }

    @Test
    @DisplayName("토큰 발급 실패(CodefAuthException) 시 execution을 호출하지 않고 그대로 전파한다")
    void intercept_propagatesAuthExceptionWithoutCallingExecution() {
        when(codefAuthClient.getAccessToken())
                .thenThrow(new CodefAuthException("토큰 발급 실패", new RuntimeException()));

        HttpRequest request = new MockClientHttpRequest();
        byte[] body = "payload".getBytes();

        CodefBearerTokenInterceptor interceptor = new CodefBearerTokenInterceptor(codefAuthClient);

        assertThatThrownBy(() -> interceptor.intercept(request, body, execution))
                .isInstanceOf(CodefAuthException.class);
    }
}
