package com.team10.backend.domain.user.dto.req;

import com.team10.backend.domain.user.type.AgeGroup;
import com.team10.backend.domain.user.type.FinancialInterest;
import com.team10.backend.domain.user.type.OccupationStatus;

import java.util.Set;

public record UserProfileReq(
        AgeGroup ageGroup,
        String region,
        OccupationStatus occupationStatus,
        Set<FinancialInterest> financialInterests
) {
}
