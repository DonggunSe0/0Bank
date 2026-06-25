package com.team10.backend.domain.youngPolicy.exception;

import com.team10.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum YoungPolicyErrorCode implements ErrorCode {

    YOUNG_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "청년 정책을 찾을 수 없습니다."),
    YOUNG_POLICY_SYNC_FAILED(HttpStatus.BAD_GATEWAY, "청년 정책 외부 API 연동에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
