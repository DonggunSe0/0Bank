package com.team10.backend.global.idempotency.aspect;

import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import com.team10.backend.global.idempotency.annotation.Idempotent;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.service.IdempotencyRequestHasher;
import com.team10.backend.global.idempotency.service.IdempotencyReservationService;
import com.team10.backend.global.idempotency.service.IdempotencyReserveResult;
import com.team10.backend.global.idempotency.service.IdempotencyService;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;


@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;
    private final IdempotencyRequestHasher idempotencyRequestHasher;
    private final IdempotencyReservationService idempotencyReservationService;
    private final TransactionTemplate transactionTemplate;
    private final HttpServletRequest httpServletRequest;


    // @Around: 메서드 실행 전, 후, 예외 발생 시점까지 전부 감쌀 수 있는 AOP 방식
    @Around("@annotation(idempotent)")
    public Object handle(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Long userId = resolveUserId();
        String idempotencyKey = resolveIdempotencyKey();
        String requestHash = generateRequestHash(idempotent.operationType());

        IdempotencyReserveResult<?> reserveResult =
                idempotencyReservationService.reserveOrResolveDuplicate(
                        userId,
                        idempotent.operationType(),
                        idempotencyKey,
                        requestHash,
                        getResponseBodyType(joinPoint)
                );

        // 최초 응답이 201 CREATED면 replay도 201 CREATED로 반환
        if (reserveResult.replay()) {
            Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();

            if (ResponseEntity.class.isAssignableFrom(returnType)) {
                return ResponseEntity
                        .status(resolveReplayStatusCode(reserveResult.responseStatusCode()))
                        .body(reserveResult.storedResponse());
            }

            return reserveResult.storedResponse(); // ResponseEntity 타입 아닌경우 그냥 DTO 반환 (방어적 로직)
        }

        Idempotency idempotency = reserveResult.idempotency();

        try {
            return executeBusinessAndCompleteSuccess(joinPoint, idempotency);
        } catch (BusinessException e) {
            idempotencyService.completeFailure(idempotency.getId());
            throw e;
        }
    }

    private int resolveReplayStatusCode(Integer statusCode) {
        return statusCode == null ? HttpStatus.OK.value() : statusCode;
    }

    private Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Number number) {
            return number.longValue();
        }

        throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
    }

    private String resolveIdempotencyKey() {
        return currentRequest().getHeader("Idempotency-Key");
    }

    private String generateRequestHash(IdempotencyOperationType operationType) {
        HttpServletRequest request = currentRequest();
        String method = request.getMethod();             // POST
        String uri = request.getRequestURI();            // /api/transfers
        String queryString = request.getQueryString();   // validate=true
        String body = resolveRequestBody(request);       // {"fromAccountId":1,"toAccountId":2,"amount":10000}

        return idempotencyRequestHasher.generate(
                operationType,
                method,
                uri,
                queryString,
                body
        );
    }

    private HttpServletRequest currentRequest() {
        // RequestContextHolder = 현재 요청 스레드에 저장된 HTTP 요청 컨텍스트 보관소
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes(); // 지금 처리 중인 요청의 request 관련 정보 조회

        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest(); // 현재 HttpServletRequest 꺼내기
        }

        return httpServletRequest;
    }

    private String resolveRequestBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = findContentCachingRequestWrapper(request);

        if (wrapper == null) {
            return "";
        }

        byte[] content = wrapper.getContentAsByteArray();

        return new String(content, StandardCharsets.UTF_8);
    }

    private ContentCachingRequestWrapper findContentCachingRequestWrapper(HttpServletRequest request) {
        HttpServletRequest currentRequest = request;

        while (currentRequest instanceof HttpServletRequestWrapper wrapper) {
            if (currentRequest instanceof ContentCachingRequestWrapper contentCachingRequestWrapper) {
                return contentCachingRequestWrapper;
            }

            ServletRequest wrappedRequest = wrapper.getRequest();

            // 만약 내부 요청이 HttpServletRequest가 아니면 더 이상 추적할 수 없으니 null 반환.
            if (!(wrappedRequest instanceof HttpServletRequest nextRequest)) {
                return null;
            }

            // 내부 요청이 HttpServletRequest가 맞으면, 자동으로 만들어진 nextRequest를 사용해서 다음 wrapper로 이동
            currentRequest = nextRequest;
        }

        return null;
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

                    Object responseBody = extractBody(response);
                    Integer responseStatusCode = extractStatusCode(response);

                    idempotencyService.completeSuccess(
                            idempotency.getId(),
                            responseBody,
                            responseStatusCode
                    );

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

    private Integer extractStatusCode(Object response) {
        if (response instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }

        return HttpStatus.OK.value(); // ResponseEntity 형태 아니면 200 OK로 저장
    }

    // 컨트롤러 반환값: ResponseEntity<TransferRes>
    // DB에 저장할 값: TransferRes
    private Object extractBody(Object response) {
        if (response instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getBody();
        }

        return response;
    }

    // IdempotencyService.reserve()는 저장된 JSON 응답을 다시 DTO로 복원해야 하므로 반환 타입이 필요함
    // 컨트롤러가 ResponseEntity<TransferRes>를 반환할 때, 실제 body 타입인 TransferRes.class를 찾아내는 메서드
    private Class<?> getResponseBodyType(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 반환 타입이 ResponseEntity인지 확인
        if (ResponseEntity.class.isAssignableFrom(signature.getReturnType())) {
            Type genericReturnType = method.getGenericReturnType(); // ResponseEntity<TransferRes>

            // ResponseEntity<TransferRes>처럼 제네릭 타입이면 ParameterizedType이다.
            if (genericReturnType instanceof ParameterizedType parameterizedType) {
                Type bodyType = parameterizedType.getActualTypeArguments()[0]; // 제네릭 안쪽 타입 추출(TransferRes)

                // 안쪽 타입이 일반 클래스면 그대로 반환
                if (bodyType instanceof Class<?> bodyClass) {
                    return bodyClass;
                }

                // ResponseEntity<List<AccountSummaryRes>> 인 경우 -> 그냥 List 반환
                // 현재 멱등성 대상이 단건 DTO만이므로 문제없음
                if (bodyType instanceof ParameterizedType bodyParameterizedType
                        && bodyParameterizedType.getRawType() instanceof Class<?> rawClass) {
                    return rawClass;
                }
            }
        }

        return signature.getReturnType();
    }

    private static class ProceedingFailure extends RuntimeException {

        private ProceedingFailure(Throwable cause) {
            super(cause);
        }
    }
}
