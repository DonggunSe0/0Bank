package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.entity.ExchangeRate;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByCurrencyCurrencyCode(CurrencyCode currencyCode);

    @Query("""
            select er
            from ExchangeRate er
            join fetch er.currency c
            where c.currencyCode in :currencyCodes
              and c.status = :status
            order by c.currencyCode asc
    """)
    List<ExchangeRate> findAllByActiveCurrencyCodes(
            @Param("currencyCodes") Collection<CurrencyCode> currencyCodes,
            @Param("status") CurrencyStatus status
    );

}
