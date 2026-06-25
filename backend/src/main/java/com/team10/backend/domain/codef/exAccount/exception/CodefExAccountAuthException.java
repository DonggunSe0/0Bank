package com.team10.backend.domain.codef.exAccount.exception;

public class CodefExAccountAuthException extends RuntimeException {

    public CodefExAccountAuthException(String message) {
        super(message);
    }

    public CodefExAccountAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
