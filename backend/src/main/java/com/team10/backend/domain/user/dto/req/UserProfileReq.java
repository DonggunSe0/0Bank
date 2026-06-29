package com.team10.backend.domain.user.dto.req;

import com.team10.backend.domain.user.type.AgeGroup;
import com.team10.backend.domain.user.type.FinancialInterest;
import com.team10.backend.domain.user.type.OccupationStatus;
import com.team10.backend.domain.user.type.Region;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UserProfileReq(
        @NotNull(message = "연령대는 필수입니다.")
        AgeGroup ageGroup,

        @NotNull(message = "지역은 필수입니다.")
        Region region,

        @NotNull(message = "직업 상태는 필수입니다.")
        OccupationStatus occupationStatus,

        // 관심분야는 선택 — UserCreateReq와 동일하게 null 허용(UserProfile에서 null-safe 처리)
        Set<FinancialInterest> financialInterests
) {
}
