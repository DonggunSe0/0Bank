package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FxWalletRepository extends JpaRepository<FxWallet, Long> {

    Optional<FxWallet> findByUserIdAndCurrencyCurrencyCode(Long userId, CurrencyCode currencyCode);

    List<FxWallet> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FxWallet> findByIdAndUserId(Long fxWalletId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from FxWallet w where w.id = :fxWalletId and w.user.id = :userId")
    Optional<FxWallet> findByIdAndUserIdForUpdate(Long fxWalletId, Long userId);
}
