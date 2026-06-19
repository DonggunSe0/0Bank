package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExAccountTransactionRepository extends JpaRepository<ExAccountTransaction, Long> {
    List<ExAccountTransaction> findAllByExAccountUserIdOrderByTransactedAtDesc(Long userId);

    List<ExAccountTransaction> findAllByExAccountIdAndExAccountUserIdOrderByTransactedAtDesc(
            Long exAccountId,
            Long userId
    );

    Optional<ExAccountTransaction> findByExAccountIdAndTransactionKey(Long exAccountId, String transactionKey);
}
