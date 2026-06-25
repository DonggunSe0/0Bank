package com.team10.backend.domain.codef.exAccount.exception;

public class CodefExAccountRegistrationException extends CodefExAccountClientException {

    private final CodefExAccountRegistrationFailure failure;

    public CodefExAccountRegistrationException(
            CodefExAccountRegistrationFailure failure,
            String message
    ) {
        super(message);
        this.failure = failure;
    }

    public CodefExAccountRegistrationException(
            CodefExAccountRegistrationFailure failure,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failure = failure;
    }

    public CodefExAccountRegistrationFailure getFailure() {
        return failure;
    }
}
