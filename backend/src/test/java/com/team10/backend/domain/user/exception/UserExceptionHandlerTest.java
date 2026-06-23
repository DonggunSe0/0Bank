package com.team10.backend.domain.user.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.global.exception.ErrorResponse;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class UserExceptionHandlerTest {

    private final UserExceptionHandler handler = new UserExceptionHandler();

    @Test
    @DisplayName("JwtException → 401 INVALID_REFRESH_TOKEN")
    void handleJwtException() {
        ResponseEntity<ErrorResponse> response = handler.handleJwtException(new JwtException("invalid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN.getCode());
    }
}
