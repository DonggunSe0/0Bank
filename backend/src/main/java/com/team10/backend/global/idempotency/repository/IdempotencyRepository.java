package com.team10.backend.global.idempotency.repository;

import com.team10.backend.global.idempotency.entity.Idempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
}
