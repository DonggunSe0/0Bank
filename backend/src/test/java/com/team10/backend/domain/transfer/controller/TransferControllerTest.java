package com.team10.backend.domain.transfer.controller;

import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.req.DepositReq;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.service.TransferService;
import com.team10.backend.domain.transfer.type.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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
    @DisplayName("입금 요청을 서비스에 위임하고 200 OK 응답을 반환한다")
    void topUp_delegatesToServiceAndReturnsOk() {
        DepositReq request = new DepositReq(1L, 100_000L, "초기 입금");
        TopUpRes response = new TopUpRes(
                10L,
                1L,
                TransactionType.DEPOSIT,
                100_000L,
                0L,
                100_000L,
                "초기 입금",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );
        when(transferService.topUp(1L, "deposit-key", 1L, 100_000L, "초기 입금")).thenReturn(response);

        ResponseEntity<TopUpRes> result = transferController.topUp(1L, "deposit-key", request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(transferService).topUp(1L, "deposit-key", 1L, 100_000L, "초기 입금");
    }

    @Test
    @DisplayName("송금 요청을 서비스에 위임하고 200 OK 응답을 반환한다")
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
        when(transferService.transfer(1L, "test-idempotency-key", 1L, "100200300002", 50_000L, "점심값")).thenReturn(response);

        ResponseEntity<TransferRes> result = transferController.transfer(1L, "test-idempotency-key", request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(transferService).transfer(1L, "test-idempotency-key", 1L, "100200300002", 50_000L, "점심값");
    }
}
