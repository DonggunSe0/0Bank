package com.team10.backend.global.idempotency.service;

import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// UK 충돌 시 reserve 재조회 흐름을 감싸는 퍼사드, self-invocation 트랜잭션 문제 해결 목적
@Service
@RequiredArgsConstructor
public class IdempotencyReservationFacade {

    private static final String IDEMPOTENCY_UNIQUE_CONSTRAINT = "uk_user_idempotency_key";

    private final IdempotencyService idempotencyService;

    // 같은 키를 가진 동시 요청이 먼저 멱등성 레코드를 생성한 경우,
    // DB UK 충돌을 기존 레코드 조회로 변환해 SUCCESS/PROCESSING/CONFLICT 정책을 적용한다.
    public <T> IdempotencyReserveResult<T> reserveOrResolveDuplicate(
            Long userId,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType
    ) {
        try {
            return idempotencyService.reserve(userId, operationType, idempotencyKey, requestHash, responseType);
        } catch (DataIntegrityViolationException e) {
            if (!isIdempotencyUniqueConstraintViolation(e)) {
                throw e; // 멱등성 유니크 예외아니면 그냥 예외 던지기
            }
            // 정확히 멱등성 유니크 예외인 경우만 1회 재조회
            return idempotencyService.reserve(userId, operationType, idempotencyKey, requestHash, responseType);
        }

    }

    // IDEMPOTENCY_UNIQUE_CONSTRAINT 예외인 경우만 true 반환
    private boolean isIdempotencyUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;

        while (current != null) {
            // Hibernate의 DB 제약 위반 예외인지 검증
            if (current instanceof ConstraintViolationException constraintViolationException) {
                // Hibernate 예외에서 실제 DB constraint 이름 꺼냄
                String constraintName = constraintViolationException.getConstraintName();

                if (isIdempotencyConstraintName(constraintName)) {
                    return true;
                }
            }

            // 어떤 환경에서는 실제로 UK 위반이 났는데도 Hibernate가 constraint name을 못 뽑아서 null을 줄 수 있다.
            // 예외메시지 예시: Unique index or primary key violation: "UK_USER_IDEMPOTENCY_KEY ..."
            String message = current.getMessage();
            if (containsIdempotencyConstraintName(message)) {
                return true;
            }

            // 또 다른 Throwable(예외) 객체 = 하위객체 | null
            current = current.getCause();
        }
        return false;
    }

    private boolean isIdempotencyConstraintName(String constraintName) {
        // equalsIgnoreCase사용: DB나 Hibernate가 constraint 이름을 대문자로 반환할 수 있기 때문
        return constraintName != null
                && constraintName.equalsIgnoreCase(IDEMPOTENCY_UNIQUE_CONSTRAINT);
    }

    private boolean containsIdempotencyConstraintName(String message) {
        return message != null
                && message.toLowerCase().contains(IDEMPOTENCY_UNIQUE_CONSTRAINT.toLowerCase());
    }

}
