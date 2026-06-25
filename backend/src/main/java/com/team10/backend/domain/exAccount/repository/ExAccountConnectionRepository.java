package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccountConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExAccountConnectionRepository extends JpaRepository<ExAccountConnection, Long> {

    Optional<ExAccountConnection> findByUserIdAndOrganization(Long userId, String organization);

    Optional<ExAccountConnection> findByIdAndUserId(Long connectionId, Long userId);
}
