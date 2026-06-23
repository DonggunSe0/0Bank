package com.team10.backend.domain.exAccount.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExAccountConnectionErrorCode implements ErrorCode {

    EX_ACCOUNT_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "외부계좌 연결정보를 찾을 수 없습니다."),
    EX_ACCOUNT_CONNECTION_INACTIVE(HttpStatus.CONFLICT, "외부계좌 연결정보를 다시 인증해야 합니다."),
    EX_ACCOUNT_CONNECTION_REGISTER_RATE_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "외부계좌 연결 등록 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
    ),
    EX_ACCOUNT_CONNECTION_ACCOUNT_LIST_RATE_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "외부계좌 후보 조회 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
    ),
    EX_ACCOUNT_CONNECTION_CONCURRENT_REQUEST(
            HttpStatus.CONFLICT,
            "현재 연결 등록 요청을 처리 중입니다. 잠시 후 다시 시도해주세요."
    );

    private final HttpStatus status;
    private final String message;
}
