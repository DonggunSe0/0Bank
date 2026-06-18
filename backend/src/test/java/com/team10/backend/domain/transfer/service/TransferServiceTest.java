package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.service.IdempotencyRequestHasher;
import com.team10.backend.global.idempotency.service.IdempotencyReserveResult;
import com.team10.backend.global.idempotency.service.IdempotencyService;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private IdempotencyRequestHasher idempotencyRequestHasher;

    @Mock
    private TransferBusinessService transferBusinessService;

    @InjectMocks
    private TransferService transferService;

    @Test
    @DisplayName("입금 요청을 선점한 뒤 비즈니스 입금을 실행하고 성공 상태를 기록한다")
    void topUp_reservedRequest_executesBusinessAndCompletesSuccess() {
        Idempotency idempotency = idempotency(9L, IdempotencyOperationType.DEPOSIT, "deposit-key");
        TopUpRes response = topUpResponse();
        when(idempotencyRequestHasher.generate(1L, 5_000L, "입금 메모")).thenReturn("deposit-request-hash");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.DEPOSIT, "deposit-key", "deposit-request-hash", TopUpRes.class))
                .thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(transferBusinessService.executeTopUp(1L, 1L, 5_000L, "입금 메모"))
                .thenReturn(response);

        TopUpRes result = transferService.topUp(1L, "deposit-key", 1L, 5_000L, "입금 메모");

        assertSame(response, result);
        InOrder inOrder = inOrder(idempotencyService, transferBusinessService);
        inOrder.verify(idempotencyService).reserve(1L, IdempotencyOperationType.DEPOSIT, "deposit-key", "deposit-request-hash", TopUpRes.class);
        inOrder.verify(transferBusinessService).executeTopUp(1L, 1L, 5_000L, "입금 메모");
        inOrder.verify(idempotencyService).completeSuccess(9L, response);
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("입금 재요청이면 비즈니스 로직을 실행하지 않고 저장된 응답을 반환한다")
    void topUp_replay_returnsStoredResponseWithoutBusinessExecution() {
        TopUpRes storedResponse = topUpResponse();
        when(idempotencyRequestHasher.generate(1L, 5_000L, "입금 메모")).thenReturn("deposit-request-hash");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.DEPOSIT, "deposit-key", "deposit-request-hash", TopUpRes.class))
                .thenReturn(IdempotencyReserveResult.replay(storedResponse));

        TopUpRes result = transferService.topUp(1L, "deposit-key", 1L, 5_000L, "입금 메모");

        assertSame(storedResponse, result);
        verify(transferBusinessService, never()).executeTopUp(any(), any(), any(), any());
        verify(idempotencyService, never()).completeSuccess(any(), any());
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("멱등성 유니크 충돌이면 reserve만 1회 재시도한다")
    void topUp_idempotencyUniqueViolation_retriesReserveOnce() {
        TopUpRes storedResponse = topUpResponse();
        DataIntegrityViolationException duplicateKeyException = idempotencyUniqueViolation();
        when(idempotencyRequestHasher.generate(1L, 5_000L, "입금 메모")).thenReturn("deposit-request-hash");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.DEPOSIT, "deposit-key", "deposit-request-hash", TopUpRes.class))
                .thenThrow(duplicateKeyException)
                .thenReturn(IdempotencyReserveResult.replay(storedResponse));

        TopUpRes result = transferService.topUp(1L, "deposit-key", 1L, 5_000L, "입금 메모");

        assertSame(storedResponse, result);
        verify(idempotencyService, times(2))
                .reserve(1L, IdempotencyOperationType.DEPOSIT, "deposit-key", "deposit-request-hash", TopUpRes.class);
        verify(transferBusinessService, never()).executeTopUp(any(), any(), any(), any());
        verify(idempotencyService, never()).completeSuccess(any(), any());
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("입금 비즈니스 예외가 발생하면 실패 상태를 기록하고 예외를 전파한다")
    void topUp_businessException_completesFailureAndRethrows() {
        Idempotency idempotency = idempotency(10L, IdempotencyOperationType.DEPOSIT, "invalid-deposit-key");
        BusinessException businessException = new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);
        when(idempotencyRequestHasher.generate(1L, 0L, "입금 메모")).thenReturn("invalid-deposit-request-hash");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.DEPOSIT, "invalid-deposit-key", "invalid-deposit-request-hash", TopUpRes.class))
                .thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(transferBusinessService.executeTopUp(1L, 1L, 0L, "입금 메모"))
                .thenThrow(businessException);

        BusinessException result = assertThrows(
                BusinessException.class,
                () -> transferService.topUp(1L, "invalid-deposit-key", 1L, 0L, "입금 메모")
        );

        assertSame(businessException, result);
        verify(idempotencyService).completeFailure(10L);
        verify(idempotencyService, never()).completeSuccess(any(), any());
    }

    @Test
    @DisplayName("송금 요청을 선점한 뒤 비즈니스 송금을 실행하고 성공 상태를 기록한다")
    void transfer_reservedRequest_executesBusinessAndCompletesSuccess() {
        Idempotency idempotency = idempotency(12L, IdempotencyOperationType.TRANSFER, "transfer-key");
        TransferRes response = transferResponse();
        when(idempotencyRequestHasher.generate(1L, "100200300002", 50_000L, "점심값")).thenReturn("transfer-request-hash");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.TRANSFER, "transfer-key", "transfer-request-hash", TransferRes.class))
                .thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(transferBusinessService.executeTransfer(1L, 1L, "100200300002", 50_000L, "점심값"))
                .thenReturn(response);

        TransferRes result = transferService.transfer(1L, "transfer-key", 1L, "100200300002", 50_000L, "점심값");

        assertSame(response, result);
        InOrder inOrder = inOrder(idempotencyService, transferBusinessService);
        inOrder.verify(idempotencyService).reserve(1L, IdempotencyOperationType.TRANSFER, "transfer-key", "transfer-request-hash", TransferRes.class);
        inOrder.verify(transferBusinessService).executeTransfer(1L, 1L, "100200300002", 50_000L, "점심값");
        inOrder.verify(idempotencyService).completeSuccess(12L, response);
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("송금 재요청이면 비즈니스 로직을 실행하지 않고 저장된 응답을 반환한다")
    void transfer_replay_returnsStoredResponseWithoutBusinessExecution() {
        TransferRes storedResponse = transferResponse();
        when(idempotencyRequestHasher.generate(1L, "100200300002", 50_000L, "점심값")).thenReturn("transfer-request-hash");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.TRANSFER, "transfer-key", "transfer-request-hash", TransferRes.class))
                .thenReturn(IdempotencyReserveResult.replay(storedResponse));

        TransferRes result = transferService.transfer(1L, "transfer-key", 1L, "100200300002", 50_000L, "점심값");

        assertSame(storedResponse, result);
        verify(transferBusinessService, never()).executeTransfer(any(), any(), any(), any(), any());
        verify(idempotencyService, never()).completeSuccess(any(), any());
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("송금 비즈니스 예외가 발생하면 실패 상태를 기록하고 예외를 전파한다")
    void transfer_businessException_completesFailureAndRethrows() {
        Idempotency idempotency = idempotency(14L, IdempotencyOperationType.TRANSFER, "insufficient-key");
        BusinessException businessException = new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
        when(idempotencyRequestHasher.generate(1L, "100200300002", 50_000L, "잔액 부족")).thenReturn("insufficient-request-hash");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.TRANSFER, "insufficient-key", "insufficient-request-hash", TransferRes.class))
                .thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(transferBusinessService.executeTransfer(1L, 1L, "100200300002", 50_000L, "잔액 부족"))
                .thenThrow(businessException);

        BusinessException result = assertThrows(
                BusinessException.class,
                () -> transferService.transfer(1L, "insufficient-key", 1L, "100200300002", 50_000L, "잔액 부족")
        );

        assertSame(businessException, result);
        verify(idempotencyService).completeFailure(14L);
        verify(idempotencyService, never()).completeSuccess(any(), any());
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

    private Idempotency idempotency(Long id, IdempotencyOperationType operationType, String idempotencyKey) {
        Idempotency idempotency = Idempotency.processing(mock(User.class), operationType, idempotencyKey, "request-hash");
        ReflectionTestUtils.setField(idempotency, "id", id);
        return idempotency;
    }

    private DataIntegrityViolationException idempotencyUniqueViolation() {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("UK_IDEMPOTENCY_USER_OPERATION_KEY");
        return new DataIntegrityViolationException("duplicate idempotency key", cause);
    }
}
