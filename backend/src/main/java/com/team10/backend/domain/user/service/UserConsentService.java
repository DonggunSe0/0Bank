package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.entity.UserConsent;
import com.team10.backend.domain.user.dto.req.ConsentUpdateReq;
import com.team10.backend.domain.user.dto.res.ConsentRes;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserConsentRepository;
import com.team10.backend.domain.user.type.TermsType;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserConsentService {

    private final UserConsentRepository userConsentRepository;

    /**
     * 회원가입 시 4가지 약관 동의 내역을 일괄 저장한다.
     */
    @Transactional
    public void saveAll(User user,
                        boolean agreedServiceTerms,
                        boolean agreedPersonalInfo,
                        boolean agreedFinancialInfo,
                        boolean agreedMarketing) {
        userConsentRepository.saveAll(List.of(
                UserConsent.of(user, TermsType.SERVICE_TERMS,  agreedServiceTerms),
                UserConsent.of(user, TermsType.PERSONAL_INFO,  agreedPersonalInfo),
                UserConsent.of(user, TermsType.FINANCIAL_INFO, agreedFinancialInfo),
                UserConsent.of(user, TermsType.MARKETING,      agreedMarketing)
        ));
    }

    /**
     * 내 약관 동의 내역 전체 조회.
     */
    public List<ConsentRes> getConsents(Long userId) {
        return userConsentRepository.findAllByUserId(userId).stream()
                .map(ConsentRes::from)
                .toList();
    }

    /**
     * 마케팅 수신 동의 변경 (선택 항목만 변경 가능).
     */
    @Transactional
    public ConsentRes updateMarketing(Long userId, ConsentUpdateReq request) {
        UserConsent consent = userConsentRepository
                .findByUserIdAndTermsType(userId, TermsType.MARKETING)
                .orElseThrow(() -> new BusinessException(UserErrorCode.CONSENT_NOT_FOUND));
        consent.update(request.marketingAgreed());
        return ConsentRes.from(consent);
    }
}
