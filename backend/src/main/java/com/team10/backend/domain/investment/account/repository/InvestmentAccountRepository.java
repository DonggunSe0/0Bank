package com.team10.backend.domain.investment.account.repository;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentAccountRepository extends JpaRepository<InvestmentAccount, Long> {

    Optional<InvestmentAccount> findByIdAndUserId(Long accountId, Long userId);

    List<InvestmentAccount> findAllByUserIdAndStatusNot(Long userId, InvestmentAccountStatus status);

    Optional<InvestmentAccount> findByIdAndUserIdAndStatusNot(
            Long accountId,
            Long userId,
            InvestmentAccountStatus status
    );

    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 비관락
    @Query("select a from InvestmentAccount a where a.id = :accountId and a.user.id = :userId")
    Optional<InvestmentAccount> findByIdAndUserIdForUpdate(Long accountId, Long userId);
}
