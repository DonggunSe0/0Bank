package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCurrencyCode(CurrencyCode currencyCode);

    List<Currency> findAllByCurrencyCodeInAndStatus(Collection<CurrencyCode> currencyCodes, CurrencyStatus status);
}
