package com.team10.backend.global.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 전역 예외 처리기.
 *
 * <p>Spring Security 필터 체인(AuthenticationException, AccessDeniedException)에서 발생하는
 * 예외는 DispatcherServlet 이전에 처리되므로 여기서 잡히지 않는다.
 * 해당 예외는 {@link com.team10.backend.global.security.JwtAuthenticationEntryPoint} 와
 * {@link com.team10.backend.global.security.JwtAccessDeniedHandler} 에서 처리한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 검증 실패 로그에 원문 값을 그대로 남기면 비밀번호/OTP 등이 평문으로 로그에 적힐 수 있다.
    // 이 클래스는 domain 비의존을 유지해야 하므로(특정 DTO를 import하지 않음) 필드명에
    // 아래 키워드가 포함되면 값을 마스킹하는 일반화된 방식으로 처리한다.
    private static final Set<String> SENSITIVE_FIELD_KEYWORDS = Set.of(
            "password", "secret", "token", "otp", "pin", "credential"
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.error("BusinessException: {}", e.getMessage());
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ErrorResponse.from(code));

    }

    // @Valid 유효성 검사 실패 시 처리. @RequestBody 검증
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        log.warn("[VALIDATION] RequestBody 검증 실패 - errorCount={}",
                e.getBindingResult().getErrorCount());

        e.getBindingResult().getFieldErrors().forEach(error ->
                log.warn("[VALIDATION] field='{}', rejectedValue='{}', message='{}'",
                        error.getField(),
                        maskIfSensitive(error.getField(), error.getRejectedValue()),
                        error.getDefaultMessage()));
        List<ValidationError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new ValidationError(err.getField(), err.getDefaultMessage()))
                .toList();

        ErrorCode errorCode = GlobalErrorCode.INVALID_INPUT_VALUE; // 공통 에러 코드 사용
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.from(errorCode, errors));
    }

    // @Validated 유효성 검사 실패 처리. @RequestParam, @PathVariable 검증
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ConstraintViolationException e) {
        log.warn("[VALIDATION] RequestParam/PathVariable 검증 실패 - violationCount={}",
                e.getConstraintViolations().size());

        e.getConstraintViolations().forEach(v -> {
            String field = extractField(v.getPropertyPath());
            log.warn("[VALIDATION] field='{}', invalidValue='{}', message='{}'",
                    field,
                    maskIfSensitive(field, v.getInvalidValue()),
                    v.getMessage());
        });
        List<ValidationError> errors = e.getConstraintViolations().stream()
                .map(v -> new ValidationError(extractField(v.getPropertyPath()), v.getMessage()))
                .toList();

        ErrorCode errorCode = GlobalErrorCode.INVALID_INPUT_VALUE; // 공통 에러 코드 사용
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode, errors));
    }

    // 필수 @RequestParam 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[MISSING_PARAM] {}", e.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.from(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    // 필수 @RequestHeader 누락
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("[MISSING_HEADER] {}", e.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.from(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    // @RequestParam 타입 변환 실패 (e.g. enum 값 불일치)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[TYPE_MISMATCH] param='{}', value='{}' — {}", e.getName(), e.getValue(), e.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.from(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    // 예상치 못한 예외는 내부 정보를 노출하지 않고 500으로 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("[UNEXPECTED] 처리되지 않은 예외 발생", e);
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.from(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }

    // ConstraintViolationException에서 필드명만 추출하는 헬퍼 메서드
    private String extractField(Path path) {
        String field = null;
        for (Path.Node node : path) {
            field = node.getName();
        }
        return field;
    }

    // 필드명에 민감 키워드가 포함되어 있으면 로그에 값 대신 마스킹 문자열을 남긴다.
    private static Object maskIfSensitive(String fieldName, Object value) {
        if (fieldName == null) {
            return value;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        boolean sensitive = SENSITIVE_FIELD_KEYWORDS.stream().anyMatch(lower::contains);
        return sensitive ? "[REDACTED]" : value;
    }

}
