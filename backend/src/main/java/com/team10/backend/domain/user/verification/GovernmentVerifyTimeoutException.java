package com.team10.backend.domain.user.verification;

/**
 * 행안부 외부 API 타임아웃 예외.
 *
 * <p>RuntimeException 이므로 @Transactional 경계에서 자동 롤백 대상이 된다.
 * OcrService 의 메인 트랜잭션은 이 예외 발생 시 롤백되어
 * OCR 파싱 결과 업데이트가 원자적으로 취소된다.
 *
 * <p>FAILED 상태 기록은 별도의 REQUIRES_NEW 트랜잭션
 * ({@link VerificationSessionRecorder})에서 수행한다.
 */
public class GovernmentVerifyTimeoutException extends RuntimeException {

    public GovernmentVerifyTimeoutException(String message) {
        super(message);
    }

    public GovernmentVerifyTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
