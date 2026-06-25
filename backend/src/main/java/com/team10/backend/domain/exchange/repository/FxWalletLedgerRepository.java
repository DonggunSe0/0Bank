package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.entity.FxWalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxWalletLedgerRepository extends JpaRepository<FxWalletLedger, Long> {
}
