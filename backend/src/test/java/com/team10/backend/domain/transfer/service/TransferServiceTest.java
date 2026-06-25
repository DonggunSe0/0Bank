package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.type.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferBusinessService transferBusinessService;

    @InjectMocks
    private TransferService transferService;

    @Test
    @DisplayName("입금 요청은 비즈니스 입금 서비스에 위임한다")
    void topUp_delegatesToBusinessService() {
        TopUpRes response = topUpResponse();
        when(transferBusinessService.executeTopUp(1L, 1L, 5_000L, "입금 메모"))
                .thenReturn(response);

        TopUpRes result = transferService.topUp(1L, 1L, 5_000L, "입금 메모");

        assertSame(response, result);
        verify(transferBusinessService).executeTopUp(1L, 1L, 5_000L, "입금 메모");
    }

    @Test
    @DisplayName("송금 요청은 비즈니스 송금 서비스에 위임한다")
    void transfer_delegatesToBusinessService() {
        TransferRes response = transferResponse();
        when(transferBusinessService.executeTransfer(1L, 1L, "100200300002", "123456", 50_000L, "점심값"))
                .thenReturn(response);

        TransferRes result = transferService.transfer(1L, 1L, "100200300002", "123456", 50_000L, "점심값");

        assertSame(response, result);
        verify(transferBusinessService).executeTransfer(1L, 1L, "100200300002", "123456", 50_000L, "점심값");
    }

    private TopUpRes topUpResponse() {
        return new TopUpRes(
                100L,
                1L,
                TransactionType.DEPOSIT,
                5_000L,
                10_000L,
                15_000L,
                "입금 메모",
                LocalDateTime.now()
        );
    }

    private TransferRes transferResponse() {
        return new TransferRes(
                20L,
                TransferStatus.SUCCESS,
                1L,
                "100200300001",
                "100200300002",
                50_000L,
                50_000L,
                "점심값",
                LocalDateTime.now()
        );
    }
}
