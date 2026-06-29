package com.team10.backend.domain.saving.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.service.SavingDepositService;
import com.team10.backend.global.lock.DistributedLockTemplate;
import java.util.function.Supplier;
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

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @InjectMocks
    private InstallmentPaymentScheduler installmentPaymentScheduler;

    @Test
    @DisplayName("적금 정기 자동이체 스케줄러는 락 안에서 정기 납입 처리 서비스를 호출한다")
    void processDueInstallmentPayments() {
        when(distributedLockTemplate.executeWithLock(
                anyString(),
                any(),
                any(),
                eq(SavingErrorCode.SAVING_SCHEDULER_LOCK_NOT_ACQUIRED),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<Integer>>getArgument(4).get());

        installmentPaymentScheduler.processDueInstallmentPayments();

        verify(savingDepositService).processDueInstallmentPayments();
    }
}
