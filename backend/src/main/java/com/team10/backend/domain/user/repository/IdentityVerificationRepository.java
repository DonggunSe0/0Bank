package com.team10.backend.domain.user.repository;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.type.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

    /** 특정 사용자의 가장 최근 인증 세션 조회 */
    Optional<IdentityVerification> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    /** 특정 상태의 인증 세션 조회 (단계 전환 시 사용) */
    Optional<IdentityVerification> findTopByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, VerificationStatus status);
}
