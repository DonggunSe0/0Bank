package com.team10.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.global.exception.ErrorResponse;
import com.team10.backend.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청(401)을 처리하는 Security EntryPoint.
 *
 * <p>JWT 토큰이 없거나 유효하지 않을 때 스프링 시큐리티 필터 체인에서 직접 호출된다.
 * DispatcherServlet까지 가지 않으므로 GlobalExceptionHandler로는 처리할 수 없다.
 *
 * <p>ErrorResponse는 String 필드만 포함하므로 JavaTimeModule 불필요 —
 * Spring Boot 4.x (Jackson 3.x) 빈과의 버전 충돌을 피해 로컬 인스턴스를 사용한다.
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        log.warn("[SECURITY] 인증 실패 — uri={}, message={}", request.getRequestURI(), authException.getMessage());

        ErrorResponse body = ErrorResponse.from(GlobalErrorCode.UNAUTHORIZED);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
