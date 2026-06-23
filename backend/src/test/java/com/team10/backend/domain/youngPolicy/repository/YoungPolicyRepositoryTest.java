package com.team10.backend.domain.youngPolicy.repository;

import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyDetailRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyExternalRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySummaryRes;
import com.team10.backend.domain.youngPolicy.entity.YoungPolicy;
import com.team10.backend.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
public class YoungPolicyRepositoryTest {

    @Autowired
    private YoungPolicyRepository youngPolicyRepository;

    public static final Long POLICY_ID = 1L;
    public static final String EXTERNAL_POLICY_ID = "YP-001";
    public static final String POLICY_TITLE = "청년 월세 지원";
    public static final String POLICY_DESCRIPTION = "청년 월세 지원 설명";
    public static final String POLICY_CATEGORY = "주거";
    public static final String POLICY_SUB_CATEGORY = "주거비";
    public static final Integer POLICY_MIN_AGE = 19;
    public static final Integer POLICY_MAX_AGE = 34;
    public static final String POLICY_REGION_CODE = "11";
    public static final String POLICY_JOB_CODE = "001";
    public static final String POLICY_APPLY_PERIOD = "20260601~20260630";
    public static final String POLICY_APPLY_URL = "https://example.com/policies/YP-001";
    public static final String POLICY_APPLY_METHOD = "온라인 신청";
    public static final String CURRENT_API_POLICY_ID = "20260305005400112100";
    public static final String CURRENT_API_CATEGORY = "금융/복지/문화";
    public static final String CURRENT_API_REGION = "서울특별시";

    @Test
    @DisplayName("정책번호로 청년 정책을 조회한다")
    void findByPolicyId_returnsPolicy() {
        YoungPolicy savedPolicy = youngPolicyRepository.save(createPolicyWithoutId());

        Optional<YoungPolicy> result = youngPolicyRepository.findByPolicyId(EXTERNAL_POLICY_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(savedPolicy.getId());
        assertThat(result.get().getPolicyId()).isEqualTo(EXTERNAL_POLICY_ID);
        assertThat(result.get().getTitle()).isEqualTo(POLICY_TITLE);
        assertThat(result.get().getDescription()).isEqualTo(POLICY_DESCRIPTION);
        assertThat(result.get().getCategory()).isEqualTo(POLICY_CATEGORY);
        assertThat(result.get().getSubCategory()).isEqualTo(POLICY_SUB_CATEGORY);
        assertThat(result.get().getMinAge()).isEqualTo(POLICY_MIN_AGE);
        assertThat(result.get().getMaxAge()).isEqualTo(POLICY_MAX_AGE);
        assertThat(result.get().getRegionCode()).isEqualTo(POLICY_REGION_CODE);
        assertThat(result.get().getJobCode()).isEqualTo(POLICY_JOB_CODE);
        assertThat(result.get().getApplyPeriod()).isEqualTo(POLICY_APPLY_PERIOD);
        assertThat(result.get().getApplyUrl()).isEqualTo(POLICY_APPLY_URL);
        assertThat(result.get().getApplyMethod()).isEqualTo(POLICY_APPLY_METHOD);
    }

    @Test
    @DisplayName("존재하지 않는 정책번호는 빈 Optional을 반환한다")
    void findByPolicyId_unknownPolicyId_returnsEmpty() {
        youngPolicyRepository.save(createPolicyWithoutId());

        Optional<YoungPolicy> result = youngPolicyRepository.findByPolicyId("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("service와 controller 테스트에서 사용할 mock 청년 정책 엔티티를 생성한다")
    void createPolicy_returnsMockPolicyEntity() {
        YoungPolicy policy = createPolicy();

        assertThat(policy.getId()).isEqualTo(POLICY_ID);
        assertThat(policy.getPolicyId()).isEqualTo(EXTERNAL_POLICY_ID);
        assertThat(policy.getTitle()).isEqualTo(POLICY_TITLE);
        assertThat(policy.getDescription()).isEqualTo(POLICY_DESCRIPTION);
        assertThat(policy.getCategory()).isEqualTo(POLICY_CATEGORY);
        assertThat(policy.getSubCategory()).isEqualTo(POLICY_SUB_CATEGORY);
        assertThat(policy.getMinAge()).isEqualTo(POLICY_MIN_AGE);
        assertThat(policy.getMaxAge()).isEqualTo(POLICY_MAX_AGE);
        assertThat(policy.getRegionCode()).isEqualTo(POLICY_REGION_CODE);
        assertThat(policy.getJobCode()).isEqualTo(POLICY_JOB_CODE);
        assertThat(policy.getApplyPeriod()).isEqualTo(POLICY_APPLY_PERIOD);
        assertThat(policy.getApplyUrl()).isEqualTo(POLICY_APPLY_URL);
        assertThat(policy.getApplyMethod()).isEqualTo(POLICY_APPLY_METHOD);
    }

    @Test
    @DisplayName("controller 테스트에서 사용할 mock 청년 정책 요약 응답을 생성한다")
    void createSummaryResponse_returnsMockSummaryResponse() {
        YoungPolicySummaryRes response = createSummaryResponse();

        assertThat(response.id()).isEqualTo(POLICY_ID);
        assertThat(response.policyId()).isEqualTo(EXTERNAL_POLICY_ID);
        assertThat(response.title()).isEqualTo(POLICY_TITLE);
        assertThat(response.category()).isEqualTo(POLICY_CATEGORY);
        assertThat(response.subCategory()).isEqualTo(POLICY_SUB_CATEGORY);
        assertThat(response.minAge()).isEqualTo(POLICY_MIN_AGE);
        assertThat(response.maxAge()).isEqualTo(POLICY_MAX_AGE);
        assertThat(response.regionCode()).isEqualTo(POLICY_REGION_CODE);
        assertThat(response.applyPeriod()).isEqualTo(POLICY_APPLY_PERIOD);
    }

    @Test
    @DisplayName("controller 테스트에서 사용할 mock 청년 정책 상세 응답을 생성한다")
    void createDetailResponse_returnsMockDetailResponse() {
        YoungPolicyDetailRes response = createDetailResponse();

        assertThat(response.id()).isEqualTo(POLICY_ID);
        assertThat(response.policyId()).isEqualTo(EXTERNAL_POLICY_ID);
        assertThat(response.title()).isEqualTo(POLICY_TITLE);
        assertThat(response.description()).isEqualTo(POLICY_DESCRIPTION);
        assertThat(response.applyUrl()).isEqualTo(POLICY_APPLY_URL);
        assertThat(response.applyMethod()).isEqualTo(POLICY_APPLY_METHOD);
    }

    @Test
    @DisplayName("service 테스트에서 사용할 OpenAPI 정책 응답을 생성한다")
    void createExternalResponse_returnsMockCurrentApiResponse() {
        YoungPolicyExternalRes response = createExternalResponse();

        assertThat(response.policyItems()).hasSize(1);
        assertThat(response.policyItems().get(0).plcyNo()).isEqualTo(CURRENT_API_POLICY_ID);
        assertThat(response.policyItems().get(0).lclsfNm()).isEqualTo(CURRENT_API_CATEGORY);
        assertThat(response.policyItems().get(0).zipCd()).isEqualTo(CURRENT_API_REGION);
    }

    @Test
    @DisplayName("현재 API 정책 응답의 소수 연령 값을 정수로 변환한다")
    void createExternalPolicyItem_parsesDecimalAgeValues() {
        YoungPolicy policy = createExternalPolicyItem(19.0, "20.0").toEntity();

        assertThat(policy.getMinAge()).isEqualTo(19);
        assertThat(policy.getMaxAge()).isEqualTo(20);
    }

    public static List<YoungPolicy> createPolicies() {
        return List.of(createPolicy());
    }

    public static YoungPolicy createPolicy() {
        YoungPolicy policy = createPolicyWithoutId();
        ReflectionTestUtils.setField(policy, "id", POLICY_ID);
        return policy;
    }

    private static YoungPolicy createPolicyWithoutId() {
        return new YoungPolicy(
                EXTERNAL_POLICY_ID,
                POLICY_TITLE,
                POLICY_DESCRIPTION,
                POLICY_CATEGORY,
                POLICY_SUB_CATEGORY,
                POLICY_MIN_AGE,
                POLICY_MAX_AGE,
                POLICY_REGION_CODE,
                POLICY_JOB_CODE,
                POLICY_APPLY_PERIOD,
                POLICY_APPLY_URL,
                POLICY_APPLY_METHOD
        );
    }

    public static YoungPolicySummaryRes createSummaryResponse() {
        return new YoungPolicySummaryRes(
                POLICY_ID,
                EXTERNAL_POLICY_ID,
                POLICY_TITLE,
                POLICY_CATEGORY,
                POLICY_SUB_CATEGORY,
                POLICY_MIN_AGE,
                POLICY_MAX_AGE,
                POLICY_REGION_CODE,
                POLICY_APPLY_PERIOD
        );
    }

    public static YoungPolicyDetailRes createDetailResponse() {
        return new YoungPolicyDetailRes(
                POLICY_ID,
                EXTERNAL_POLICY_ID,
                POLICY_TITLE,
                POLICY_DESCRIPTION,
                POLICY_CATEGORY,
                POLICY_SUB_CATEGORY,
                POLICY_MIN_AGE,
                POLICY_MAX_AGE,
                POLICY_REGION_CODE,
                POLICY_JOB_CODE,
                POLICY_APPLY_PERIOD,
                POLICY_APPLY_URL,
                POLICY_APPLY_METHOD
        );
    }

    public static YoungPolicyExternalRes createExternalResponse() {
        return new YoungPolicyExternalRes(
                new YoungPolicyExternalRes.Result(List.of(createExternalPolicyItem()))
        );
    }

    public static YoungPolicyExternalRes.PolicyItem createExternalPolicyItem() {
        return createExternalPolicyItem(19, 20);
    }

    public static YoungPolicyExternalRes.PolicyItem createExternalPolicyItem(Object minAge, Object maxAge) {
        return new YoungPolicyExternalRes.PolicyItem(
                CURRENT_API_POLICY_ID,
                "청년문화예술패스",
                "청년의 문화향유 기회를 제공",
                CURRENT_API_CATEGORY,
                "문화",
                minAge,
                maxAge,
                CURRENT_API_REGION,
                POLICY_JOB_CODE,
                "20260225 ~ 20260630",
                "https://example.com",
                "온라인 신청"
        );
    }
}
