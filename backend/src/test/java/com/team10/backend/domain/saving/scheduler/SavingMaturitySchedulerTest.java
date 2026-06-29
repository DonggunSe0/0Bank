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
class SavingMaturitySchedulerTest {

    @Mock
    private SavingDepositService savingDepositService;

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @InjectMocks
    private SavingMaturityScheduler savingMaturityScheduler;

    @Test
    @DisplayName("만기 처리 스케줄러는 락 안에서 만기 대상 저축 일괄 처리 서비스를 호출한다")
    void matureDueSavings() {
        when(distributedLockTemplate.executeWithLock(
                anyString(),
                any(),
                any(),
                eq(SavingErrorCode.SAVING_SCHEDULER_LOCK_NOT_ACQUIRED),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<Integer>>getArgument(4).get());

        savingMaturityScheduler.matureDueSavings();

        verify(savingDepositService).matureDueSavings();
    }
}
