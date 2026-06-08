package com.team10.backend.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    HttpStatus getStatus();

    String name();

    default String getCode() {
        return name();
    } // frontend가 사용하는 식별자

    String getMessage();
}
