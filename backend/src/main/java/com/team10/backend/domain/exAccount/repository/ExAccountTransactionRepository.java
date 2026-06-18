package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExAccountTransactionRepository extends JpaRepository<ExAccountTransaction, Long> {
    List<ExAccountTransaction> findAllByExAccountUserIdOrderByTransactedAtDesc(Long userId);
}
