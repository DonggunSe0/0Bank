package com.team10.backend.domain.investment.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.client.auth.dto.KisWebSocketApprovalKey;
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
class KisWebSocketApprovalKeyManagerTest {

    @Mock
    private KisAuthClient kisAuthClient;

    private KisWebSocketApprovalKeyManager approvalKeyManager;

    @BeforeEach
    void setUp() {
        approvalKeyManager = new KisWebSocketApprovalKeyManager(kisAuthClient);
    }

    @Test
    @DisplayName("최초 호출 시 웹소켓 접속키를 발급하고 이후 호출에서는 캐시된 접속키를 반환한다")
    void getApprovalKeyUsesCachedKey() {
        when(kisAuthClient.issueWebSocketApprovalKey())
                .thenReturn(new KisWebSocketApprovalKey("approval-key"));

        assertThat(approvalKeyManager.getApprovalKey()).isEqualTo("approval-key");
        assertThat(approvalKeyManager.getApprovalKey()).isEqualTo("approval-key");

        verify(kisAuthClient, times(1)).issueWebSocketApprovalKey();
    }

    @Test
    @DisplayName("웹소켓 접속키 발급 실패 시 예외를 전파하고 다음 호출에서 다시 발급을 시도한다")
    void getApprovalKeyRetriesOnNextCallAfterIssueFailure() {
        RuntimeException exception = new RuntimeException("issue failed");
        when(kisAuthClient.issueWebSocketApprovalKey())
                .thenThrow(exception)
                .thenReturn(new KisWebSocketApprovalKey("approval-key"));

        assertThatThrownBy(() -> approvalKeyManager.getApprovalKey())
                .isSameAs(exception);
        assertThat(approvalKeyManager.getApprovalKey()).isEqualTo("approval-key");

        verify(kisAuthClient, times(2)).issueWebSocketApprovalKey();
    }

    @Test
    @DisplayName("여러 스레드가 동시에 최초 웹소켓 접속키를 요청해도 발급은 한 번만 수행한다")
    void getApprovalKeyIssuesOnlyOnceWhenCalledConcurrently() throws Exception {
        int threadCount = 8;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        when(kisAuthClient.issueWebSocketApprovalKey()).thenAnswer(invocation -> {
            Thread.sleep(100);
            return new KisWebSocketApprovalKey("shared-key");
        });

        try {
            List<Future<String>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executorService.submit(() -> {
                        ready.countDown();
                        start.await();
                        return approvalKeyManager.getApprovalKey();
                    }))
                    .toList();

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<String> future : futures) {
                assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo("shared-key");
            }

            verify(kisAuthClient, times(1)).issueWebSocketApprovalKey();
        } finally {
            executorService.shutdownNow();
        }
    }
}
