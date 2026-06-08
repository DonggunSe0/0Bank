package com.team10.backend.global.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
                        error.getRejectedValue(),
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

        e.getConstraintViolations().forEach(v ->
                log.warn("[VALIDATION] field='{}', invalidValue='{}', message='{}'",
                        extractField(v.getPropertyPath()),
                        v.getInvalidValue(),
                        v.getMessage()));
        List<ValidationError> errors = e.getConstraintViolations().stream()
                .map(v -> new ValidationError(extractField(v.getPropertyPath()), v.getMessage()))
                .toList();

        ErrorCode errorCode = GlobalErrorCode.INVALID_INPUT_VALUE; // 공통 에러 코드 사용
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode, errors));
    }

    // ConstraintViolationException에서 필드명만 추출하는 헬퍼 메서드
    private String extractField(Path path) {
        String field = null;
        for (Path.Node node : path) {
            field = node.getName();
        }
        return field;
    }

}
