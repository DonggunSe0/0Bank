package com.team10.backend.global.exception;

public record ValidationError(
        String field,
        String reason
) {
}