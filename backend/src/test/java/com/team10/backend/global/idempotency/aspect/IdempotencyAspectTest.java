package com.team10.backend.global.idempotency.aspect;

import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.idempotency.annotation.Idempotent;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.service.IdempotencyRequestHasher;
import com.team10.backend.global.idempotency.service.IdempotencyReservationService;
import com.team10.backend.global.idempotency.service.IdempotencyReserveResult;
import com.team10.backend.global.idempotency.service.IdempotencyService;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private IdempotencyRequestHasher idempotencyRequestHasher;

    @Mock
    private IdempotencyReservationService idempotencyReservationService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Test
    @DisplayName("새 요청을 선점하면 실제 메서드를 실행하고 성공 응답을 저장한다")
    void handle_reservedRequest_proceedsAndCompletesSuccess() throws Throwable {
        IdempotencyAspect aspect = aspect();
        Method method = fixtureMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        Idempotency idempotency = idempotency(9L);
        TopUpRes response = topUpResponse();
        givenJoinPoint(method);
        when(idempotencyRequestHasher.generate(IdempotencyOperationType.TOPUP, 1L, 5_000L, "입금 메모"))
                .thenReturn("request-hash");
        when(idempotencyReservationService.reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        )).thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(joinPoint.proceed()).thenReturn(response);
        executeTransactionCallback();

        Object result = aspect.handle(joinPoint, idempotent);

        assertSame(response, result);
        InOrder inOrder = inOrder(idempotencyReservationService, joinPoint, idempotencyService);
        inOrder.verify(idempotencyReservationService).reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        );
        inOrder.verify(joinPoint).proceed();
        inOrder.verify(idempotencyService).completeSuccess(9L, response);
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("재요청이면 실제 메서드를 실행하지 않고 저장된 응답을 반환한다")
    void handle_replay_returnsStoredResponseWithoutProceeding() throws Throwable {
        IdempotencyAspect aspect = aspect();
        Method method = fixtureMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        TopUpRes storedResponse = topUpResponse();
        givenJoinPoint(method);
        when(idempotencyRequestHasher.generate(IdempotencyOperationType.TOPUP, 1L, 5_000L, "입금 메모"))
                .thenReturn("request-hash");
        when(idempotencyReservationService.reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        )).thenReturn(IdempotencyReserveResult.replay(storedResponse));

        Object result = aspect.handle(joinPoint, idempotent);

        assertSame(storedResponse, result);
        verify(joinPoint, never()).proceed();
        verify(idempotencyService, never()).completeSuccess(any(), any());
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("비즈니스 예외가 발생하면 실패 상태를 기록하고 예외를 전파한다")
    void handle_businessException_completesFailureAndRethrows() throws Throwable {
        IdempotencyAspect aspect = aspect();
        Method method = fixtureMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        Idempotency idempotency = idempotency(10L);
        BusinessException businessException = new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);
        givenJoinPoint(method);
        when(idempotencyRequestHasher.generate(IdempotencyOperationType.TOPUP, 1L, 5_000L, "입금 메모"))
                .thenReturn("request-hash");
        when(idempotencyReservationService.reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        )).thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(joinPoint.proceed()).thenThrow(businessException);
        executeTransactionCallback();

        BusinessException result = assertThrows(
                BusinessException.class,
                () -> aspect.handle(joinPoint, idempotent)
        );

        assertSame(businessException, result);
        verify(idempotencyService).completeFailure(10L);
        verify(idempotencyService, never()).completeSuccess(any(), any());
    }

    private IdempotencyAspect aspect() {
        return new IdempotencyAspect(
                idempotencyService,
                idempotencyRequestHasher,
                idempotencyReservationService,
                transactionTemplate
        );
    }

    @SuppressWarnings("unchecked")
    private void executeTransactionCallback() {
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<Object> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
    }

    private void givenJoinPoint(Method method) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getReturnType()).thenReturn(TopUpRes.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, "deposit-key", 1L, 5_000L, "입금 메모"});
    }

    private Method fixtureMethod() throws NoSuchMethodException {
        return Fixture.class.getMethod(
                "topUp",
                Long.class,
                String.class,
                Long.class,
                Long.class,
                String.class
        );
    }

    private Idempotency idempotency(Long id) {
        Idempotency idempotency = Idempotency.processing(
                mock(User.class),
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash"
        );
        ReflectionTestUtils.setField(idempotency, "id", id);
        return idempotency;
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

    private static class Fixture {

        @Idempotent(
                operationType = IdempotencyOperationType.TOPUP,
                userId = "#userId",
                key = "#idempotencyKey",
                hashFields = {"#accountId", "#amount", "#memo"}
        )
        public TopUpRes topUp(
                Long userId,
                String idempotencyKey,
                Long accountId,
                Long amount,
                String memo
        ) {
            return null;
        }
    }
}
