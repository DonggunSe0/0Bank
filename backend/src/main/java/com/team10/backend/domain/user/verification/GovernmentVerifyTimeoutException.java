package com.team10.backend.domain.user.verification;

/** 행안부 외부 API 타임아웃 예외. */
public class GovernmentVerifyTimeoutException extends RuntimeException {

    public GovernmentVerifyTimeoutException(String message) {
        super(message);
    }

    public GovernmentVerifyTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
