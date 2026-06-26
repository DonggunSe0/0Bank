package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.entity.ExchangeOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrder, Long> {
    boolean existsByExchangeQuote_Id(Long exchangeQuoteId);

    Optional<ExchangeOrder> findByIdAndUserId(Long exchangeOrderId, Long userId);

    Page<ExchangeOrder> findAllByUserId(Long userId, Pageable pageable);
}
