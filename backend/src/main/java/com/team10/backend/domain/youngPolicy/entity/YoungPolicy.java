package com.team10.backend.domain.youngPolicy.entity;

import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YoungPolicy extends BaseEntity {

    @Column(unique = true)
    private String policyId;    // 정책번호 (plcyNo)

    private String title;       // 정책명 (plcyNm)

    @Column(columnDefinition = "TEXT")
    private String description; // 정책설명내용 (plcyExplnCn)

    private String category;    // 정책대분류명 (lclsfNm)
    private String subCategory; // 정책중분류명 (mclsfNm)

    private Integer minAge;     // 지원대상최소연령 (sprtTrgtMinAge)
    private Integer maxAge;     // 지원대상최대연령 (sprtTrgtMaxAge)
    private String regionCode;  // 정책거주지역코드 (zipCd)
    private String jobCode;     // 정책취업요건코드 (jobCd)

    private String applyPeriod; // 신청기간 (aplyYmd)

    @Column(columnDefinition = "TEXT")
    private String applyUrl;    // 신청URL주소 (aplyUrlAddr)

    @Column(columnDefinition = "TEXT")
    private String applyMethod; // 정책신청방법내용 (plcyAplyMthdCn)

    // 12개 항목을 모두 받는 생성자
    public YoungPolicy(String policyId,
                       String title,
                       String description,
                       String category,
                       String subCategory,
                       Integer minAge,
                       Integer maxAge,
                       String regionCode,
                       String jobCode,
                       String applyPeriod,
                       String applyUrl,
                       String applyMethod) {
        this.policyId = policyId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.subCategory = subCategory;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.regionCode = regionCode;
        this.jobCode = jobCode;
        this.applyPeriod = applyPeriod;
        this.applyUrl = applyUrl;
        this.applyMethod = applyMethod;
    }
}
