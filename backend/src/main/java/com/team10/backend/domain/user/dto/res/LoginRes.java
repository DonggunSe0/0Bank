package com.team10.backend.domain.user.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 로그인 성공 응답.
 * refreshToken은 HttpOnly 쿠키로 전달되므로 JSON 바디에서 제외한다.
 */
public record LoginRes(String accessToken, String refreshToken, UserRes user) {

    /** Jackson 직렬화 시 응답 바디에서 제외 (HttpOnly 쿠키로 전달). */
    @JsonIgnore
    @Override
    public String refreshToken() {
        return refreshToken;
    }
}
