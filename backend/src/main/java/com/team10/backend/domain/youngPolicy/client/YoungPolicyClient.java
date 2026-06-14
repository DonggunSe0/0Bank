package com.team10.backend.domain.youngPolicy.client;

import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyReq;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyRes;

//외부 api 응답에 대한 통신규칙 정의를 위한 인터페이스
public interface YoungPolicyClient {
    YoungPolicyRes fetchPolicies(YoungPolicyReq request);
}
