package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Modifying
    @Query(value = "INSERT INTO external_asset_transactions ( " +
            "  external_account_id, transaction_key, transacted_at, direction, " +
            "  amount, balance_after, counterparty_name, memo, raw_category, " +
            "  created_at, updated_at " +
            ") VALUES ( " +
            "  :exAccountId, :transactionKey, :transactedAt, :direction, " +
            "  :amount, :balanceAfter, :counterpartyName, :memo, :rawCategory, " +
            "  :createdAt, :updatedAt " +
            ") ON DUPLICATE KEY UPDATE " +
            "  transacted_at = :transactedAt, " +
            "  direction = :direction, " +
            "  amount = :amount, " +
            "  balance_after = :balanceAfter, " +
            "  counterparty_name = :counterpartyName, " +
            "  memo = :memo, " +
            "  raw_category = :rawCategory, " +
            "  updated_at = :updatedAt", nativeQuery = true)
    int upsert(
            @Param("exAccountId") Long exAccountId,
            @Param("transactionKey") String transactionKey,
            @Param("transactedAt") LocalDateTime transactedAt,
            @Param("direction") String direction,
            @Param("amount") BigDecimal amount,
            @Param("balanceAfter") BigDecimal balanceAfter,
            @Param("counterpartyName") String counterpartyName,
            @Param("memo") String memo,
            @Param("rawCategory") String rawCategory,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
