package com.team10.backend.global.idempotency.service;

import com.team10.backend.global.idempotency.entity.Idempotency;

public record IdempotencyReserveResult<T>(
        boolean replay,
        Idempotency idempotency,
        T storedResponse,
        Integer responseStatusCode
) {

    public static <T> IdempotencyReserveResult<T> reserved(Idempotency idempotency) {
        return new IdempotencyReserveResult<>(false, idempotency, null, null);
    }

    public static <T> IdempotencyReserveResult<T> replay(T storedResponse, Integer responseStatusCode) {
        return new IdempotencyReserveResult<>(true, null, storedResponse, responseStatusCode);
    }
}
