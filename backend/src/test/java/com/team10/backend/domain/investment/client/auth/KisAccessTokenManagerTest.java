package com.team10.backend.domain.investment.client.auth;

import static com.team10.backend.domain.investment.config.KisConstants.SEOUL_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.client.auth.dto.KisAccessToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KisAccessTokenManagerTest {

    @Mock
    private KisAuthClient kisAuthClient;

    private KisAccessTokenManager accessTokenManager;

    @BeforeEach
    void setUp() {
        accessTokenManager = new KisAccessTokenManager(kisAuthClient);
    }

    @Test
    @DisplayName("최초 호출 시 AccessToken을 발급하고 이후 호출에서는 캐시된 토큰을 반환한다")
    void getAccessTokenUsesCachedToken() {
        when(kisAuthClient.issueAccessToken())
                .thenReturn(token("access-token", 60));

        assertThat(accessTokenManager.getAccessToken()).isEqualTo("access-token");
        assertThat(accessTokenManager.getAccessToken()).isEqualTo("access-token");

        verify(kisAuthClient, times(1)).issueAccessToken();
    }

    @Test
    @DisplayName("토큰 만료 시간이 버퍼 이내이면 다음 호출에서 재발급한다")
    void getAccessTokenRefreshesWhenTokenExpiresWithinBuffer() {
        when(kisAuthClient.issueAccessToken())
                .thenReturn(token("old-token", 10))
                .thenReturn(token("new-token", 60));

        assertThat(accessTokenManager.getAccessToken()).isEqualTo("old-token");
        assertThat(accessTokenManager.getAccessToken()).isEqualTo("new-token");

        verify(kisAuthClient, times(2)).issueAccessToken();
    }

    @Test
    @DisplayName("토큰 발급 실패 시 예외를 전파하고 다음 호출에서 다시 발급을 시도한다")
    void getAccessTokenRetriesOnNextCallAfterIssueFailure() {
        RuntimeException exception = new RuntimeException("issue failed");
        when(kisAuthClient.issueAccessToken())
                .thenThrow(exception)
                .thenReturn(token("access-token", 60));

        assertThatThrownBy(() -> accessTokenManager.getAccessToken())
                .isSameAs(exception);
        assertThat(accessTokenManager.getAccessToken()).isEqualTo("access-token");

        verify(kisAuthClient, times(2)).issueAccessToken();
    }

    @Test
    @DisplayName("애플리케이션 종료 시 발급된 AccessToken을 폐기하고 캐시를 비운다")
    void revokeOnShutdownRevokesIssuedTokenAndClearsCache() {
        when(kisAuthClient.issueAccessToken())
                .thenReturn(token("access-token", 60))
                .thenReturn(token("next-token", 60));

        assertThat(accessTokenManager.getAccessToken()).isEqualTo("access-token");

        accessTokenManager.revokeOnShutdown();

        verify(kisAuthClient).revokeAccessToken("access-token");
        assertThat(accessTokenManager.getAccessToken()).isEqualTo("next-token");
        verify(kisAuthClient, times(2)).issueAccessToken();
    }

    @Test
    @DisplayName("발급된 AccessToken이 없으면 종료 시 폐기 API를 호출하지 않는다")
    void revokeOnShutdownDoesNothingWhenTokenIsNotIssued() {
        accessTokenManager.revokeOnShutdown();

        verify(kisAuthClient, never()).revokeAccessToken(anyString());
    }

    @Test
    @DisplayName("AccessToken 폐기 실패 시에도 캐시를 비운다")
    void revokeOnShutdownClearsCacheEvenWhenRevokeFails() {
        when(kisAuthClient.issueAccessToken())
                .thenReturn(token("access-token", 60))
                .thenReturn(token("next-token", 60));
        doThrow(new RuntimeException("revoke failed"))
                .when(kisAuthClient)
                .revokeAccessToken("access-token");

        assertThat(accessTokenManager.getAccessToken()).isEqualTo("access-token");

        accessTokenManager.revokeOnShutdown();

        assertThat(accessTokenManager.getAccessToken()).isEqualTo("next-token");
        verify(kisAuthClient, times(2)).issueAccessToken();
    }

    @Test
    @DisplayName("여러 스레드가 동시에 최초 AccessToken을 요청해도 발급은 한 번만 수행한다")
    void getAccessTokenIssuesOnlyOnceWhenCalledConcurrently() throws Exception {
        /** 멀티 스레드 준비 */
        int threadCount = 8;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        when(kisAuthClient.issueAccessToken()).thenAnswer(invocation -> {
            Thread.sleep(100);
            return token("shared-token", 60);
        });

        try {
            /** 스레드풀에서 수행할 작업 생성 및 제출, Future를 이용해 확인 */
            List<Future<String>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executorService.submit(() -> {
                        ready.countDown();
                        start.await(); /** 각 스레드가 시작 신호 올 때까지 대기 */
                        return accessTokenManager.getAccessToken();
                    }))
                    .toList();

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue(); /** 메인스레드에서 대기 */
            start.countDown(); /** 동시에 멀티 스레드 작업 수행하도록 지시하는 트리거, 동시에 getAccessToken() 수행 */

            /** Future안에 담긴 각 스레드의 실행 결과 확인 */
            for (Future<String> future : futures) {
                assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo("shared-token");
            }

            verify(kisAuthClient, times(1)).issueAccessToken();
        } finally {
            executorService.shutdownNow();
        }
    }

    private KisAccessToken token(String accessToken, long expiresAfterMinutes) {
        return new KisAccessToken(accessToken, LocalDateTime.now(SEOUL_ZONE).plusMinutes(expiresAfterMinutes));
    }
}
