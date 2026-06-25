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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.http.HttpServletRequestWrapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("새 요청을 선점하면 실제 메서드를 실행하고 성공 응답을 저장한다")
    void handle_reservedRequest_proceedsAndCompletesSuccess() throws Throwable {
        IdempotencyAspect aspect = aspect();
        Method method = fixtureMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        Idempotency idempotency = idempotency(9L);
        TopUpRes response = topUpResponse();
        ResponseEntity<TopUpRes> responseEntity = ResponseEntity.status(HttpStatus.CREATED).body(response);
        givenJoinPoint(method);
        givenAuthentication();
        when(idempotencyRequestHasher.generate(
                IdempotencyOperationType.TOPUP,
                "POST",
                "/api/v1/transfers/topUp",
                null,
                ""
        ))
                .thenReturn("request-hash");
        when(idempotencyReservationService.reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        )).thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(joinPoint.proceed()).thenReturn(responseEntity);
        executeTransactionCallback();

        Object result = aspect.handle(joinPoint, idempotent);

        assertSame(responseEntity, result);
        InOrder inOrder = inOrder(idempotencyReservationService, joinPoint, idempotencyService);
        inOrder.verify(idempotencyReservationService).reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        );
        inOrder.verify(joinPoint).proceed();
        inOrder.verify(idempotencyService).completeSuccess(9L, response, HttpStatus.CREATED.value());
        verify(idempotencyService, never()).completeFailure(any());
    }

    @Test
    @DisplayName("요청 body가 중첩된 ContentCachingRequestWrapper에 있으면 request hash에 포함한다")
    void handle_cachedBodyInNestedWrapper_includesBodyInRequestHash() throws Throwable {
        IdempotencyAspect aspect = aspect();
        Method method = fixtureMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        Idempotency idempotency = idempotency(9L);
        TopUpRes response = topUpResponse();
        ResponseEntity<TopUpRes> responseEntity = ResponseEntity.status(HttpStatus.CREATED).body(response);
        String requestBody = "{\"accountId\":1,\"amount\":5000,\"memo\":\"입금 메모\"}";
        givenJoinPoint(method);
        givenAuthentication();
        givenCachedRequestBody(requestBody);
        when(idempotencyRequestHasher.generate(
                IdempotencyOperationType.TOPUP,
                "POST",
                "/api/v1/transfers/topUp",
                null,
                requestBody
        ))
                .thenReturn("request-hash");
        when(idempotencyReservationService.reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        )).thenReturn(IdempotencyReserveResult.reserved(idempotency));
        when(joinPoint.proceed()).thenReturn(responseEntity);
        executeTransactionCallback();

        Object result = aspect.handle(joinPoint, idempotent);

        assertSame(responseEntity, result);
        verify(idempotencyRequestHasher).generate(
                IdempotencyOperationType.TOPUP,
                "POST",
                "/api/v1/transfers/topUp",
                null,
                requestBody
        );
    }

    @Test
    @DisplayName("재요청이면 실제 메서드를 실행하지 않고 저장된 응답을 반환한다")
    void handle_replay_returnsStoredResponseWithoutProceeding() throws Throwable {
        IdempotencyAspect aspect = aspect();
        Method method = fixtureMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        TopUpRes storedResponse = topUpResponse();
        givenJoinPoint(method);
        givenAuthentication();
        when(idempotencyRequestHasher.generate(
                IdempotencyOperationType.TOPUP,
                "POST",
                "/api/v1/transfers/topUp",
                null,
                ""
        ))
                .thenReturn("request-hash");
        when(idempotencyReservationService.reserveOrResolveDuplicate(
                1L,
                IdempotencyOperationType.TOPUP,
                "deposit-key",
                "request-hash",
                TopUpRes.class
        )).thenReturn(IdempotencyReserveResult.replay(storedResponse, HttpStatus.CREATED.value()));

        Object result = aspect.handle(joinPoint, idempotent);

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(storedResponse, response.getBody());
        verify(joinPoint, never()).proceed();
        verify(idempotencyService, never()).completeSuccess(any(), any(), any());
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
        givenAuthentication();
        when(idempotencyRequestHasher.generate(
                IdempotencyOperationType.TOPUP,
                "POST",
                "/api/v1/transfers/topUp",
                null,
                ""
        ))
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
        verify(idempotencyService, never()).completeSuccess(any(), any(), any());
    }

    private IdempotencyAspect aspect() {
        return new IdempotencyAspect(
                idempotencyService,
                idempotencyRequestHasher,
                idempotencyReservationService,
                transactionTemplate,
                request()
        );
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers/topUp");
        request.addHeader("Idempotency-Key", "deposit-key");
        return request;
    }

    private void givenCachedRequestBody(String requestBody) throws Exception {
        MockHttpServletRequest request = request();
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));

        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request, 1024 * 1024);
        cachingRequest.getInputStream().readAllBytes();

        HttpServletRequestWrapper nestedRequest = new HttpServletRequestWrapper(cachingRequest);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(nestedRequest));
    }

    private void givenAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null));
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
        when(methodSignature.getReturnType()).thenReturn(ResponseEntity.class);
    }

    private Method fixtureMethod() throws NoSuchMethodException {
        return Fixture.class.getMethod(
                "topUp"
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
                operationType = IdempotencyOperationType.TOPUP
        )
        public ResponseEntity<TopUpRes> topUp() {
            return null;
        }
    }
}
