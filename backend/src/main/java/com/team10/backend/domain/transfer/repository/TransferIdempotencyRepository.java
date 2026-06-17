package com.team10.backend.domain.transfer.repository;

import com.team10.backend.domain.transfer.entity.TransferIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferIdempotencyRepository extends JpaRepository<TransferIdempotency, Long> {

    // user.id를 명확히 표현하기 위해 메서드명 User_Id로 지정
    Optional<TransferIdempotency> findByUser_IdAndIdempotencyKey(Long userId, String idempotencyKey);
}
