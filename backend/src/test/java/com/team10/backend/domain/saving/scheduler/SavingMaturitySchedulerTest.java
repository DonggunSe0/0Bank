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
class SavingMaturitySchedulerTest {

    @Mock
    private SavingDepositService savingDepositService;

    @InjectMocks
    private SavingMaturityScheduler savingMaturityScheduler;

    @Test
    @DisplayName("만기 처리 스케줄러는 만기 대상 저축 일괄 처리 서비스를 호출한다")
    void matureDueSavings() {
        savingMaturityScheduler.matureDueSavings();

        verify(savingDepositService).matureDueSavings();
    }
}
