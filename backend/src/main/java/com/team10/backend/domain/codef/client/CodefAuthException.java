package com.team10.backend.domain.codef.client;

/**
 * CODEF OAuth 토큰 발급/파싱 실패 시 발생하는 예외.
 *
 * <p>{@code BusinessException}이 아닌 {@code RuntimeException}으로 둔다.
 * 호출부({@link CodefOcrClient}, {@link CodefBankTransferService})가 공통 {@code catch (Exception e)}
 * 블록에서 각자의 도메인 에러코드(OCR_FAILED, ONE_WON_TRANSFER_FAILED 등)로 변환하므로,
 * 이 예외가 {@code BusinessException}이면 호출부의 {@code catch (BusinessException e) { throw e; }}에
 * 먼저 잡혀 의도한 도메인 에러코드로 변환되지 않고 그대로 새어나간다.
 */
public class CodefAuthException extends RuntimeException {

    public CodefAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
