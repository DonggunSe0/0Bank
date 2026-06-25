package com.team10.backend.domain.user.repository;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.type.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

    /** 특정 사용자의 가장 최근 인증 세션 조회 */
    Optional<IdentityVerification> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    /** 특정 상태의 인증 세션 조회 (단계 전환 시 사용) */
    Optional<IdentityVerification> findTopByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, VerificationStatus status);

    /**
     * OCR 비동기 처리(트랜잭션 종료 후)에서 {@code verification.getUser()}로 본인 명의를 대조하기 위해
     * user를 fetch join으로 함께 로딩한다. 일반 {@code findById}만 쓰면 user가 지연 로딩 프록시로 남아
     * 세션이 끝난 뒤 접근 시 LazyInitializationException이 발생한다.
     */
    @Query("SELECT v FROM IdentityVerification v JOIN FETCH v.user WHERE v.id = :id")
    Optional<IdentityVerification> findByIdWithUser(@Param("id") Long id);
}
