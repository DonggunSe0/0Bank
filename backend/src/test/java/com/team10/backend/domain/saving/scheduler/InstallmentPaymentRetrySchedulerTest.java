package com.team10.backend.domain.saving.scheduler;

import static org.mockito.Mockito.verify;

import com.team10.backend.domain.saving.service.SavingDepositService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstallmentPaymentRetrySchedulerTest {

    @Mock
    private SavingDepositService savingDepositService;

    @InjectMocks
    private InstallmentPaymentRetryScheduler installmentPaymentRetryScheduler;

    @Test
    @DisplayName("적금 자동이체 재시도 스케줄러는 실패 납입 재시도 서비스를 호출한다")
    void retryFailedInstallmentPayments() {
        installmentPaymentRetryScheduler.retryFailedInstallmentPayments();

        verify(savingDepositService).retryFailedInstallmentPayments();
    }
}
