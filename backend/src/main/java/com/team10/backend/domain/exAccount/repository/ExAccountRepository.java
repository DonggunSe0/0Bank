package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExAccountRepository extends JpaRepository<ExAccount, Long> {
    List<ExAccount> findAllByUserId(Long userId);

    Optional<ExAccount> findByIdAndUserId(Long id, Long userId);

    // 같은 사용자 + 같은 기관 + 같은 계좌번호 해시이면 같은 외부 계좌로 본다.
    Optional<ExAccount> findByUserIdAndOrganizationAndAccountNumberHash(
            Long userId,
            String organization,
            String accountNumberHash
    );

    @Modifying
    @Query(value = "INSERT INTO external_account ( " +
            "  user_id, organization, account_number_hash, account_number_masked, " +
            "  account_name, account_alias, asset_type, balance, withdrawable_amount, " +
            "  opened_at, maturity_at, last_transaction_at, status, created_at, updated_at " +
            ") VALUES ( " +
            "  :userId, :organization, :accountNumberHash, :accountNumberMasked, " +
            "  :accountName, :accountAlias, :assetType, :balance, :withdrawableAmount, " +
            "  :openedAt, :maturityAt, :lastTransactionAt, :status, :createdAt, :updatedAt " +
            ") ON DUPLICATE KEY UPDATE " +
            "  account_name = :accountName, " +
            "  account_alias = :accountAlias, " +
            "  balance = :balance, " +
            "  withdrawable_amount = :withdrawableAmount, " +
            "  maturity_at = :maturityAt, " +
            "  last_transaction_at = :lastTransactionAt, " +
            "  status = :status, " +
            "  updated_at = :updatedAt", nativeQuery = true)
    int upsert(
            @Param("userId") Long userId,
            @Param("organization") String organization,
            @Param("accountNumberHash") String accountNumberHash,
            @Param("accountNumberMasked") String accountNumberMasked,
            @Param("accountName") String accountName,
            @Param("accountAlias") String accountAlias,
            @Param("assetType") String assetType,
            @Param("balance") BigDecimal balance,
            @Param("withdrawableAmount") BigDecimal withdrawableAmount,
            @Param("openedAt") LocalDate openedAt,
            @Param("maturityAt") LocalDate maturityAt,
            @Param("lastTransactionAt") LocalDate lastTransactionAt,
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
