package com.team10.backend.domain.user.repository;

import com.team10.backend.domain.user.entity.UserConsent;
import com.team10.backend.domain.user.type.TermsType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findAllByUserId(Long userId);

    Optional<UserConsent> findByUserIdAndTermsType(Long userId, TermsType termsType);
}
