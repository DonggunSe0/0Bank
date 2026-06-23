package com.team10.backend.domain.user.dto.res;

import com.team10.backend.domain.user.entity.UserConsent;
import com.team10.backend.domain.user.type.TermsType;

import java.time.LocalDateTime;

public record ConsentRes(
        TermsType termsType,
        Boolean agreed,
        LocalDateTime agreedAt
) {
    public static ConsentRes from(UserConsent consent) {
        return new ConsentRes(
                consent.getTermsType(),
                consent.getAgreed(),
                consent.getAgreedAt()
        );
    }
}
