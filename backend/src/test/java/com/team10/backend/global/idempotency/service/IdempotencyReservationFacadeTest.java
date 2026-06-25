package com.team10.backend.global.idempotency.service;

import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyReservationFacadeTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("멱등성 UK 충돌이면 기존 레코드를 조회해 상태별 정책으로 처리한다")
    void reserveOrResolveDuplicate_idempotencyUniqueViolation_resolvesExistingRecord() {
        IdempotencyReservationService facade = new IdempotencyReservationService(idempotencyService);
        DataIntegrityViolationException duplicateKeyException = idempotencyUniqueViolation();
        TopUpRes storedResponse = topUpResponse();
        when(idempotencyService.reserve(1L, IdempotencyOperationType.TOPUP, "deposit-key", "request-hash", TopUpRes.class))
                .thenThrow(duplicateKeyException)
                .thenReturn(IdempotencyReserveResult.replay(storedResponse, 200));

        IdempotencyReserveResult<TopUpRes> result = facade.reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        );

        assertSame(storedResponse, result.storedResponse());
        verify(idempotencyService, times(2))
                .reserve(1L, IdempotencyOperationType.TOPUP, "deposit-key", "request-hash", TopUpRes.class);
    }

    @Test
    @DisplayName("멱등성 UK 충돌이 아니면 예외를 그대로 전파한다")
    void reserveOrResolveDuplicate_nonIdempotencyUniqueViolation_rethrows() {
        IdempotencyReservationService facade = new IdempotencyReservationService(idempotencyService);
        DataIntegrityViolationException exception = new DataIntegrityViolationException("other unique violation");
        when(idempotencyService.reserve(1L, IdempotencyOperationType.TOPUP, "deposit-key", "request-hash", TopUpRes.class))
                .thenThrow(exception);

        DataIntegrityViolationException result = assertThrows(
                DataIntegrityViolationException.class,
                () -> facade.reserveOrResolveDuplicate(
                        1L,
                        IdempotencyOperationType.TOPUP,
                        "deposit-key",
                        "request-hash",
                        TopUpRes.class
                )
        );

        assertSame(exception, result);
        verify(idempotencyService)
                .reserve(1L, IdempotencyOperationType.TOPUP, "deposit-key", "request-hash", TopUpRes.class);
    }

    private DataIntegrityViolationException idempotencyUniqueViolation() {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("UK_USER_IDEMPOTENCY_KEY");
        return new DataIntegrityViolationException("duplicate idempotency key", cause);
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
}
