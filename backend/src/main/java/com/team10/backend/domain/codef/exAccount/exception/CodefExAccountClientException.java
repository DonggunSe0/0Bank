package com.team10.backend.domain.codef.exAccount.exception;

public class CodefExAccountClientException extends RuntimeException {

    public CodefExAccountClientException(String message) {
        super(message);
    }

    public CodefExAccountClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
