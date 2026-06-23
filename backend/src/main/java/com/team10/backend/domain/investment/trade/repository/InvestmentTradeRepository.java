package com.team10.backend.domain.investment.trade.repository;

import com.team10.backend.domain.investment.trade.entity.InvestmentTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentTradeRepository extends JpaRepository<InvestmentTrade, Long> {
}
