package com.team10.backend.domain.account.repository;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.type.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByUserId(Long userId);

    List<Account> findAllByUserIdAndStatusNot(Long userId, AccountStatus status);

    Optional<Account> findByIdAndUserId(Long accountId, Long userId);

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByAccountNumber(String accountNumber);

    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :accountId")
    Optional<Account> findByIdForUpdate(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :accountId and a.user.id = :userId")
    Optional<Account> findByIdAndUserIdForUpdate(Long accountId, Long userId);
}
