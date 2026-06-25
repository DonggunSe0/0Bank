package com.team10.backend.domain.youngPolicy.client;

import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyReq;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyExternalRes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class YoungPolicyClientImpl implements YoungPolicyClient {

    private static final String BASE_URL = "https://www.youthcenter.go.kr";
    private static final String POLICY_LIST_PATH = "/go/ythip/getPlcy";
    private static final String RESPONSE_TYPE = "json";

    private final RestClient restClient;
    private final String apiKey;

    public YoungPolicyClientImpl(
            RestClient restClient,
            @Value("${young-policy.api-key:}") String apiKey
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public YoungPolicyExternalRes fetchPolicies(YoungPolicyReq request) {
        java.util.Objects.requireNonNull(request, "request must not be null");

        // 공식 OpenAPI는 GET 쿼리 파라미터로 인증키와 페이징 값을 받습니다.
        return restClient.get()
                .uri(BASE_URL + POLICY_LIST_PATH
                                + "?apiKeyNm={apiKeyNm}&pageNum={pageNum}&pageSize={pageSize}&rtnType={rtnType}",
                        apiKey,
                        request.pageNum(),
                        request.pageSize(),
                        RESPONSE_TYPE)
                .retrieve()
                .body(YoungPolicyExternalRes.class);
    }
}
