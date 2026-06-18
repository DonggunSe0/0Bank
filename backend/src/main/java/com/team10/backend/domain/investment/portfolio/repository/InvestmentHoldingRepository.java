package com.team10.backend.domain.investment.portfolio.repository;

import com.team10.backend.domain.investment.portfolio.entity.InvestmentHolding;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentHoldingRepository extends JpaRepository<InvestmentHolding, Long> {

    boolean existsByInvestmentAccountId(Long investmentAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h
            from InvestmentHolding h
            where h.investmentAccount.id = :investmentAccountId
              and h.stock.id = :stockId
            """)
    Optional<InvestmentHolding> findByInvestmentAccountIdAndStockIdForUpdate(Long investmentAccountId, Long stockId);
}
