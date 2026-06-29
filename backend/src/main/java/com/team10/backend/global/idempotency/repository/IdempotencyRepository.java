package com.team10.backend.global.idempotency.repository;

import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.type.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<Idempotency, Long> {

    Optional<Idempotency> findByUser_IdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("""
        select i
        from Idempotency i
        where i.status = com.team10.backend.global.idempotency.type.IdempotencyStatus.PROCESSING
          and i.createdAt < :threshold
        """)
    List<Idempotency> findStaleProcessing(@Param("threshold") LocalDateTime threshold);

    // 멱등키는 최초 요청일(createdAt) 기준 15일간 유효하므로,
    // 종료 상태 레코드만 보관 기간 이후 일괄 삭제한다.
    // JPQL bulk delete는 영속성 컨텍스트를 우회하므로 실행 전 flush, 실행 후 clear한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from Idempotency i
        where i.status in :statuses
          and i.createdAt < :threshold
        """)
    int deleteExpiredRecords(
            @Param("statuses") Collection<IdempotencyStatus> statuses,
            @Param("threshold") LocalDateTime threshold
    );
}
