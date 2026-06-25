package com.team10.backend.domain.youngPolicy.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.team10.backend.domain.youngPolicy.entity.YoungPolicy;
import org.springframework.util.StringUtils;

import java.util.List;

// 청년센터 응답을 담습니다. 모르는 필드는 무시해 API 변경에 대비합니다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoungPolicyExternalRes(
        Result result // 공식 OpenAPI는 result 안에 목록을 담습니다.
) {
    public YoungPolicyExternalRes {
        if (result == null) {
            result = new Result(List.of());
        }
    }

    public List<PolicyItem> policyItems() {
        // 서비스는 공식 OpenAPI의 정책 목록만 사용합니다.
        return result.youthPolicyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            List<PolicyItem> youthPolicyList
    ) {
        public Result {
            if (youthPolicyList == null) {
                youthPolicyList = List.of();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolicyItem(
            String plcyNo,         // 정책번호
            String plcyNm,         // 정책명
            String plcyExplnCn,    // 정책 상세한 설명
            String lclsfNm,        // 정책 카테고리 대분류
            String mclsfNm,        // 정책 카테고리 중분류
            Object sprtTrgtMinAge, // 최소연령
            Object sprtTrgtMaxAge, // 최대연령
            String zipCd,          // 정책거주지역 코드
            String jobCd,          // 정책 취업요건 코드
            String aplyYmd,        // 신청기간
            String aplyUrlAddr,    // 신청URL 주소
            String plcyAplyMthdCn  // 정책신청 방법 설명
    ) {
        public boolean hasPolicyId() {
            return StringUtils.hasText(plcyNo);
        }

        // 외부 API 응답을 DB 엔티티로 바꿉니다.
        public YoungPolicy toEntity() {
            return new YoungPolicy(
                    plcyNo,
                    plcyNm,
                    plcyExplnCn,
                    lclsfNm,
                    mclsfNm,
                    parseAge(sprtTrgtMinAge),
                    parseAge(sprtTrgtMaxAge),
                    zipCd,
                    jobCd,
                    aplyYmd,
                    aplyUrlAddr,
                    plcyAplyMthdCn
            );
        }

        // 이미 저장된 정책이면 최신 값으로 덮어씁니다.
        public void update(YoungPolicy policy) {
            policy.updateFrom(
                    plcyNm,
                    plcyExplnCn,
                    lclsfNm,
                    mclsfNm,
                    parseAge(sprtTrgtMinAge),
                    parseAge(sprtTrgtMaxAge),
                    zipCd,
                    jobCd,
                    aplyYmd,
                    aplyUrlAddr,
                    plcyAplyMthdCn
            );
        }

        private Integer parseAge(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }

            String strValue = value.toString();
            if (!StringUtils.hasText(strValue)) {
                return null;
            }

            try {
                // "19.0"처럼 내려온 값도 19로 저장합니다.
                return (int) Double.parseDouble(strValue);
            } catch (NumberFormatException e) {
                return null;
            }
        }

    }
}
