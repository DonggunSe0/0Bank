package com.team10.backend.global.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 검증 실패 로그가 비밀번호/OTP 등 민감한 필드 값을 평문으로 남기지 않는지 확인한다.
 * (응답 본문(ValidationError)에는 원래도 값이 포함되지 않으므로, 로그 출력만 검증한다)
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logger.setLevel(Level.DEBUG); // 테스트 환경의 logback 설정과 무관하게 WARN 로그가 항상 캡처되도록 보장
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class)).detachAppender(appender);
    }

    private String logMessages() {
        StringBuilder sb = new StringBuilder();
        appender.list.forEach(event -> sb.append(event.getFormattedMessage()).append('\n'));
        return sb.toString();
    }

    @Test
    @DisplayName("@RequestBody 검증 실패 — password 필드 값은 로그에 마스킹되고, 일반 필드 값은 그대로 남는다")
    void handleValidation_methodArgumentNotValid_masksSensitiveFields() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "userCreateReq");
        bindingResult.addError(new FieldError("userCreateReq", "password", "plain-text-pw-1234",
                false, null, null, "비밀번호 형식이 올바르지 않습니다"));
        bindingResult.addError(new FieldError("userCreateReq", "nickname", "bad nickname!!",
                false, null, null, "닉네임 형식이 올바르지 않습니다"));

        MethodParameter parameter = mock(MethodParameter.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        handler.handleValidation(ex);

        String logs = logMessages();
        assertThat(logs).doesNotContain("plain-text-pw-1234");
        assertThat(logs).contains("field='password'", "[REDACTED]");
        // 민감하지 않은 필드는 디버깅을 위해 값이 그대로 로그에 남아야 한다
        assertThat(logs).contains("bad nickname!!");
    }

    @Test
    @DisplayName("@RequestParam/@PathVariable 검증 실패 — otp 필드 값은 로그에 마스킹된다")
    void handleValidation_constraintViolation_masksSensitiveFields() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mockPath("otpCode");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getInvalidValue()).thenReturn("123456");
        when(violation.getMessage()).thenReturn("OTP 형식이 올바르지 않습니다");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        handler.handleValidation(ex);

        String logs = logMessages();
        assertThat(logs).doesNotContain("123456");
        assertThat(logs).contains("field='otpCode'", "[REDACTED]");
    }

    private Path mockPath(String fieldName) {
        Path.Node node = mock(Path.Node.class);
        when(node.getName()).thenReturn(fieldName);
        Path path = mock(Path.class);
        when(path.iterator()).thenAnswer(invocation -> List.of(node).iterator());
        return path;
    }
}
