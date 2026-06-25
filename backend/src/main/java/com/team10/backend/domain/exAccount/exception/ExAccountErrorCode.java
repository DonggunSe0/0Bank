package com.team10.backend.domain.exAccount.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExAccountErrorCode implements ErrorCode {

    EX_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "외부 계좌를 찾을 수 없습니다."),
    EX_ACCOUNT_CANDIDATE_NOT_FOUND(HttpStatus.GONE, "외부 계좌 연동 대기 세션이 만료되었거나 찾을 수 없습니다. 다시 조회해주세요."),
    EX_ACCOUNT_CANDIDATE_INVALID_INDEX(HttpStatus.BAD_REQUEST, "선택한 외부 계좌 인덱스가 유효하지 않습니다."),
    EX_ACCOUNT_CANDIDATE_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 처리 중인 외부 계좌 연동 요청입니다. 다시 조회해주세요."),
    EX_ACCOUNT_CONCURRENT_SYNC(HttpStatus.CONFLICT, "현재 외부 계좌 연동 또는 동기화가 진행 중입니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
