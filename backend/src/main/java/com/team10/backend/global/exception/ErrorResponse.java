package com.team10.backend.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) // null일 경우 JSON 응답에서 제외
        List<ValidationError> details
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                null
        );
    }

    // valid 검증 실패 에러는 details 포함
    public static ErrorResponse from(ErrorCode errorCode, List<ValidationError> details) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                details
        );
    }
}
