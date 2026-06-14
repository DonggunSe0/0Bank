package com.team10.backend.domain.youngPolicy.dto.req;

public record YoungPolicyReq(
        String apiKeyNm, // api 인증키
        Integer pageNum,
        Integer pageSize
        // 필요한 검색 조건 이후에 추가
) {
}
