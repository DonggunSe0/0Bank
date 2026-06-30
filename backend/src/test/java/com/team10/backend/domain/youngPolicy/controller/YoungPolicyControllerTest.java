package com.team10.backend.domain.youngPolicy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyReq;
import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyRecommendReq;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyDetailRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySummaryRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySyncRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyRecommendRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyRecommendItem;
import com.team10.backend.domain.youngPolicy.repository.YoungPolicyRepositoryTest;
import com.team10.backend.domain.youngPolicy.service.YoungPolicyService;
import com.team10.backend.domain.youngPolicy.service.PolicyRagRecommendService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(YoungPolicyController.class)
class YoungPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YoungPolicyService youngPolicyService;

    @MockitoBean
    private PolicyRagRecommendService policyRagRecommendService;

    @Test
    @DisplayName("청년 정책 목록 조회 API는 정책 요약 목록을 반환한다")
    void getPolicies_returnsPolicySummaries() throws Exception {
        YoungPolicySummaryRes response = YoungPolicyRepositoryTest.createSummaryResponse();
        when(youngPolicyService.getPolicies()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/youth-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(YoungPolicyRepositoryTest.POLICY_ID))
                .andExpect(jsonPath("$[0].policyId").value(YoungPolicyRepositoryTest.EXTERNAL_POLICY_ID))
                .andExpect(jsonPath("$[0].title").value(YoungPolicyRepositoryTest.POLICY_TITLE))
                .andExpect(jsonPath("$[0].category").value(YoungPolicyRepositoryTest.POLICY_CATEGORY))
                .andExpect(jsonPath("$[0].subCategory").value(YoungPolicyRepositoryTest.POLICY_SUB_CATEGORY))
                .andExpect(jsonPath("$[0].minAge").value(YoungPolicyRepositoryTest.POLICY_MIN_AGE))
                .andExpect(jsonPath("$[0].maxAge").value(YoungPolicyRepositoryTest.POLICY_MAX_AGE))
                .andExpect(jsonPath("$[0].regionCode").value(YoungPolicyRepositoryTest.POLICY_REGION_CODE))
                .andExpect(jsonPath("$[0].applyPeriod").value(YoungPolicyRepositoryTest.POLICY_APPLY_PERIOD));

        verify(youngPolicyService).getPolicies();
    }

    @Test
    @DisplayName("청년 정책 상세 조회 API는 정책 상세를 반환한다")
    void getPolicy_returnsPolicyDetail() throws Exception {
        YoungPolicyDetailRes response = YoungPolicyRepositoryTest.createDetailResponse();
        when(youngPolicyService.getPolicy(YoungPolicyRepositoryTest.POLICY_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/youth-policies/{id}", YoungPolicyRepositoryTest.POLICY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(YoungPolicyRepositoryTest.POLICY_ID))
                .andExpect(jsonPath("$.policyId").value(YoungPolicyRepositoryTest.EXTERNAL_POLICY_ID))
                .andExpect(jsonPath("$.title").value(YoungPolicyRepositoryTest.POLICY_TITLE))
                .andExpect(jsonPath("$.description").value(YoungPolicyRepositoryTest.POLICY_DESCRIPTION))
                .andExpect(jsonPath("$.category").value(YoungPolicyRepositoryTest.POLICY_CATEGORY))
                .andExpect(jsonPath("$.subCategory").value(YoungPolicyRepositoryTest.POLICY_SUB_CATEGORY))
                .andExpect(jsonPath("$.minAge").value(YoungPolicyRepositoryTest.POLICY_MIN_AGE))
                .andExpect(jsonPath("$.maxAge").value(YoungPolicyRepositoryTest.POLICY_MAX_AGE))
                .andExpect(jsonPath("$.regionCode").value(YoungPolicyRepositoryTest.POLICY_REGION_CODE))
                .andExpect(jsonPath("$.jobCode").value(YoungPolicyRepositoryTest.POLICY_JOB_CODE))
                .andExpect(jsonPath("$.applyPeriod").value(YoungPolicyRepositoryTest.POLICY_APPLY_PERIOD))
                .andExpect(jsonPath("$.applyUrl").value(YoungPolicyRepositoryTest.POLICY_APPLY_URL))
                .andExpect(jsonPath("$.applyMethod").value(YoungPolicyRepositoryTest.POLICY_APPLY_METHOD));

        verify(youngPolicyService).getPolicy(YoungPolicyRepositoryTest.POLICY_ID);
    }

    @Test
    @DisplayName("추천 경로는 상세 조회 ID로 매칭하지 않는다")
    void getRecommendPath_doesNotMatchDetailRoute() throws Exception {
        mockMvc.perform(get("/api/v1/youth-policies/recommend"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("청년 정책 동기화 API는 외부 호출 없이 service mock 결과를 반환한다")
    void syncPolicies_returnsMockedSyncResult() throws Exception {
        YoungPolicySyncRes response = new YoungPolicySyncRes(1, 1, 0, 0);
        when(youngPolicyService.syncPolicies(any(YoungPolicyReq.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/youth-policies/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNum": 1,
                                  "pageSize": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchedCount").value(1))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(0));

        verify(youngPolicyService).syncPolicies(new YoungPolicyReq(1, 10));
    }

    @Test
    @DisplayName("RAG 기반 청년 정책 추천 API는 맞춤 정책 카드 목록을 반환한다")
    void recommendPolicies_returnsMockedRecommendResult() throws Exception {
        YoungPolicyRecommendRes response = new YoungPolicyRecommendRes(List.of(
                new YoungPolicyRecommendItem(100L, "PL001", "청년 월세 지원", "주거지원", "주거비", 19, 39, "003002001", "2026", "주거비 부담을 덜어주기 위해 지원 대상으로 추천합니다.")
        ));
        when(policyRagRecommendService.recommend(any(YoungPolicyRecommendReq.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/youth-policies/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "age": 25,
                                  "region": "서울",
                                  "category": "주거지원",
                                  "query": "월세 지원을 받고 싶어요."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedPolicies[0].id").value(100L))
                .andExpect(jsonPath("$.recommendedPolicies[0].policyId").value("PL001"))
                .andExpect(jsonPath("$.recommendedPolicies[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.recommendedPolicies[0].recommendReason").value("주거비 부담을 덜어주기 위해 지원 대상으로 추천합니다."));

        verify(policyRagRecommendService).recommend(any(YoungPolicyRecommendReq.class));
    }
}
