package com.team10.backend.global.idempotency.type;

public enum IdempotencyStatus {
    PROCESSING,
    SUCCESS,
    FAILED,
    EXPIRED
}
