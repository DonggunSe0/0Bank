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
 * <p>ObjectMapper를 DI받지 않고 static 인스턴스를 사용한다.
 * Security 컨텍스트 초기화가 JacksonAutoConfiguration보다 먼저 실행되어
 * ObjectMapper 빈 주입 타이밍이 엇갈리는 문제를 방지하기 위함이다.
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
