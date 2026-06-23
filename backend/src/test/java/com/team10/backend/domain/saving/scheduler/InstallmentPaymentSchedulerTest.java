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
class InstallmentPaymentSchedulerTest {

    @Mock
    private SavingDepositService savingDepositService;

    @InjectMocks
    private InstallmentPaymentScheduler installmentPaymentScheduler;

    @Test
    @DisplayName("적금 정기 자동이체 스케줄러는 정기 납입 처리 서비스를 호출한다")
    void processDueInstallmentPayments() {
        installmentPaymentScheduler.processDueInstallmentPayments();

        verify(savingDepositService).processDueInstallmentPayments();
    }
}
