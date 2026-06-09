package com.team10.backend.domain.transfer.controller;

import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.req.DepositReq;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.dto.res.DepositRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.service.TransferService;
import com.team10.backend.domain.transfer.type.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferController transferController;

    @Test
    void deposit_delegatesToServiceAndReturnsOk() {
        DepositReq request = new DepositReq(1L, 100_000L, "초기 입금");
        DepositRes response = new DepositRes(
                10L,
                1L,
                TransactionType.DEPOSIT,
                100_000L,
                0L,
                100_000L,
                "초기 입금",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );
        when(transferService.deposit(1L, 100_000L, "초기 입금")).thenReturn(response);

        ResponseEntity<DepositRes> result = transferController.deposit(request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(transferService).deposit(1L, 100_000L, "초기 입금");
    }

    @Test
    void transfer_delegatesToServiceAndReturnsOk() {
        TransferReq request = new TransferReq(1L, "100200300002", 50_000L, "점심값");
        TransferRes response = new TransferRes(
                20L,
                TransferStatus.SUCCESS,
                1L,
                "100200300001",
                "100200300002",
                50_000L,
                50_000L,
                "점심값",
                LocalDateTime.of(2026, 6, 9, 10, 10)
        );
        when(transferService.transfer(1L, "100200300002", 50_000L, "점심값")).thenReturn(response);

        ResponseEntity<TransferRes> result = transferController.transfer(request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(transferService).transfer(1L, "100200300002", 50_000L, "점심값");
    }
}
