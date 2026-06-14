package com.team10.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.global.exception.ErrorResponse;
import com.team10.backend.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 권한 없는 요청(403)을 처리하는 Security AccessDeniedHandler.
 *
 * <p>인증은 됐지만 필요한 권한이 없을 때 스프링 시큐리티 필터 체인에서 직접 호출된다.
 * DispatcherServlet까지 가지 않으므로 GlobalExceptionHandler로는 처리할 수 없다.
 *
 * <p>ObjectMapper를 DI받지 않고 static 인스턴스를 사용한다.
 * Security 컨텍스트 초기화가 JacksonAutoConfiguration보다 먼저 실행되어
 * ObjectMapper 빈 주입 타이밍이 엇갈리는 문제를 방지하기 위함이다.
 */
@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        log.warn("[SECURITY] 접근 거부 — uri={}, message={}", request.getRequestURI(), accessDeniedException.getMessage());

        ErrorResponse body = ErrorResponse.from(GlobalErrorCode.FORBIDDEN);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
