package com.team10.backend.global.idempotency.aspect;

import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import com.team10.backend.global.idempotency.annotation.Idempotent;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.service.IdempotencyRequestHasher;
import com.team10.backend.global.idempotency.service.IdempotencyReservationService;
import com.team10.backend.global.idempotency.service.IdempotencyReserveResult;
import com.team10.backend.global.idempotency.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;
    private final IdempotencyRequestHasher idempotencyRequestHasher;
    private final IdempotencyReservationService idempotencyReservationService;
    private final TransactionTemplate transactionTemplate;
    private final HttpServletRequest httpServletRequest;

    private final SpelExpressionParser parser = new SpelExpressionParser(); // 문자열로 된 SpEL 표현식을 읽는 객체
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer(); // 메서드 파라미터 이름을 알아내는 도구
    // key = SpEL 표현식 문자열, value = 파싱된 Expression 객체
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(); // 파싱된 Expression 객체를 캐싱

    // @Around: 메서드 실행 전, 후, 예외 발생 시점까지 전부 감쌀 수 있는 AOP 방식
    // "@annotation(idempotent)":
    @Around("@annotation(idempotent)")
    public Object handle(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Long userId = resolveUserId(joinPoint, idempotent.userId());
        String idempotencyKey = resolveString(joinPoint, idempotent.key());

        String requestHash = generateRequestHash(joinPoint, idempotent);

        IdempotencyReserveResult<?> reserveResult = idempotencyReservationService.reserveOrResolveDuplicate(
                userId,
                idempotent.operationType(),
                idempotencyKey,
                requestHash,
                getReturnType(joinPoint)
        );

        if (reserveResult.replay()) {
            return reserveResult.storedResponse();
        }

        Idempotency idempotency = reserveResult.idempotency();

        try {
            return executeBusinessAndCompleteSuccess(joinPoint, idempotency);
        } catch (BusinessException e) {
            idempotencyService.completeFailure(idempotency.getId());
            throw e;
        }
    }

    private Object executeBusinessAndCompleteSuccess(
            ProceedingJoinPoint joinPoint,
            Idempotency idempotency
    ) throws Throwable {
        // 비즈니스 처리와 멱등성 SUCCESS 기록을 같은 트랜잭션에 묶어 둘 중 하나만 커밋되는 상태를 방지한다.
        try {
            return transactionTemplate.execute(status -> {
                try {
                    Object response = joinPoint.proceed(); // @Idempotent가 붙은 메서드 전체를 실행
                    idempotencyService.completeSuccess(idempotency.getId(), response);
                    return response;
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new ProceedingFailure(e);
                }
            });
        } catch (ProceedingFailure e) {
            throw e.getCause();
        }
    }

    // 어노테이션에 적은 "#userId"를 실제 Long 값으로 바꾸는 helper
    private Long resolveUserId(ProceedingJoinPoint joinPoint, String expression) {
        Object value = evaluate(joinPoint, expression);

        if (value instanceof Long longValue) {
            return longValue;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalArgumentException("userId expression must resolve to a number");
    }

    // "#idempotencyKey"를 실제 String 값으로 바꾸는 helper
    private String resolveString(ProceedingJoinPoint joinPoint, String expression) {
        Object value = evaluate(joinPoint, expression);

        if (value == null) {
            return null;
        }

        if (value instanceof String stringValue) {
            return stringValue;
        }

        throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_KEY_INVALID);
    }

    // SpEL 표현식을 실제 메서드 파라미터 값으로 해석하는 공통 helper
    private Object evaluate(ProceedingJoinPoint joinPoint, String expression) { // joinPoint = 지금 Aspect가 감싸고 있는 메서드 호출 정보
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 메서드의 서명 정보
        Method method = signature.getMethod(); // 자바가 런타임에 메서드 정보를 표현하는 객체인 Method

        // SpEL이 메서드 파라미터를 읽을 수 있도록 만들어주는 컨텍스트
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null,
                method,                 // 현재 실행중인 메서드
                joinPoint.getArgs(),    // 실제 호출된 인자값 배열
                parameterNameDiscoverer // 파라미터 이름을 찾는 도구
        );

        // 캐시값 없으면 문자열 표현식을 SpEL Expression 객체로 파싱 후 저장
        return expressionCache
                .computeIfAbsent(expression, parser::parseExpression)
                .getValue(context);
    }

    // operationType + hashFields에 적은 값들을 모아서 SHA-256 hash 생성
    private String generateRequestHash(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        List<Object> values = new ArrayList<>();
        values.add(idempotent.operationType()); // 항상 operationType을 먼저 추가

        for (String hashField : idempotent.hashFields()) {
            values.add(evaluate(joinPoint, hashField));
        }

        return idempotencyRequestHasher.generate(values.toArray());
    }

    // IdempotencyService.reserve()는 저장된 JSON 응답을 다시 DTO로 복원해야 하므로 반환 타입이 필요함
    // TransferService.transfer() 반환 타입이 TransferRes -> TransferRes.class
    private Class<?> getReturnType(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getReturnType();
    }

    private static class ProceedingFailure extends RuntimeException {

        private ProceedingFailure(Throwable cause) {
            super(cause);
        }
    }
}
