package com.team10.backend.domain.exAccount.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExAccountConnectionErrorCode implements ErrorCode {

    EX_ACCOUNT_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "외부 계좌 연결 정보를 찾을 수 없습니다."),
    EX_ACCOUNT_CONNECTION_INACTIVE(HttpStatus.CONFLICT, "외부 계좌 연결 정보가 만료되었거나 비활성 상태입니다. 다시 인증해주세요."),
    EX_ACCOUNT_CONNECTION_REGISTER_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "외부 계좌 연결 등록 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    EX_ACCOUNT_CONNECTION_ACCOUNT_LIST_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "외부 계좌 후보 조회 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    EX_ACCOUNT_CONNECTION_CONCURRENT_REQUEST(HttpStatus.CONFLICT, "외부 계좌 연결 요청을 처리 중입니다. 잠시 후 다시 시도해주세요."),
    EX_ACCOUNT_CONNECTION_CREDENTIAL_INVALID(HttpStatus.BAD_REQUEST, "외부 계좌 금융기관 인증 정보가 올바르지 않습니다."),
    EX_ACCOUNT_CONNECTION_ADDITIONAL_AUTH_REQUIRED(HttpStatus.CONFLICT, "외부 계좌 금융기관 추가 인증이 필요합니다. 현재는 ID/PW 방식만 지원합니다."),
    EX_ACCOUNT_CONNECTION_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 계좌 금융기관 연동이 일시적으로 원활하지 않습니다. 잠시 후 다시 시도해주세요."),
    EX_ACCOUNT_CONNECTION_PROVIDER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "외부 계좌 금융기관 응답을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
