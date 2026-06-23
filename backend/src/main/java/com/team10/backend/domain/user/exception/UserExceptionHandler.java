package com.team10.backend.domain.user.exception;

import com.team10.backend.global.exception.ErrorResponse;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** domain.user 전용 예외 처리기 (domain-scoped advice). */
@Slf4j
@RestControllerAdvice(basePackages = "com.team10.backend.domain.user")
public class UserExceptionHandler {

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(JwtException e) {
        log.warn("[JWT] refresh 토큰 파싱 실패: {}", e.getMessage());
        UserErrorCode code = UserErrorCode.INVALID_REFRESH_TOKEN;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.from(code));
    }
}
