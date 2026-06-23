package com.team10.backend.domain.investment.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

import com.team10.backend.domain.investment.stock.service.StockMasterSyncService;
import com.team10.backend.global.exception.BusinessException;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClientException;

class StockMasterSyncRetryRunnerTest {

    @Test
    @DisplayName("종목 마스터 동기화 실행을 서비스에 위임한다")
    void sync() {
        StockMasterSyncService stockMasterSyncService = org.mockito.Mockito.mock(StockMasterSyncService.class);
        StockMasterSyncRetryRunner retryRunner = new StockMasterSyncRetryRunner(stockMasterSyncService);

        retryRunner.sync("daily");

        verify(stockMasterSyncService).syncKospiMaster();
    }

    @Test
    @DisplayName("외부 API 계열 예외에 대해 3회 재시도하도록 설정되어 있다")
    void retryablePolicy() throws NoSuchMethodException {
        Retryable retryable = StockMasterSyncRetryRunner.class
                .getMethod("sync", String.class)
                .getAnnotation(Retryable.class);

        assertThat(retryable).isNotNull();
        assertThat(retryable.maxAttempts()).isEqualTo(3);
        assertThat(Arrays.asList(retryable.retryFor()))
                .containsExactlyInAnyOrder(BusinessException.class, RestClientException.class);
        assertThat(retryable.backoff().delay()).isEqualTo(3_000L);
        assertThat(retryable.backoff().multiplier()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("재시도 소진 후 recover는 예외를 밖으로 전파하지 않는다")
    void recoverDoesNotThrow() {
        StockMasterSyncService stockMasterSyncService = org.mockito.Mockito.mock(StockMasterSyncService.class);
        StockMasterSyncRetryRunner retryRunner = new StockMasterSyncRetryRunner(stockMasterSyncService);

        assertThatCode(() -> retryRunner.recover(new RuntimeException("failed"), "daily"))
                .doesNotThrowAnyException();
    }
}
