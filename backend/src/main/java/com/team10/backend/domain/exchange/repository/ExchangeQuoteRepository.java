package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.entity.ExchangeQuote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeQuoteRepository extends JpaRepository<ExchangeQuote, Long> {
}
