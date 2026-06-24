package com.team10.backend.domain.investment.trade.service;

import com.team10.backend.domain.investment.trade.dto.req.MarketOrderCreateReq;
import com.team10.backend.domain.investment.trade.dto.res.InvestmentTradeRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvestmentTradeService {

    private final InvestmentTradeBusinessService investmentTradeBusinessService;

    public InvestmentTradeRes createMarketOrder(
            Long userId,
            String idempotencyKey,
            MarketOrderCreateReq request
    ) {
        return investmentTradeBusinessService.executeMarketOrder(userId, idempotencyKey, request);
    }
}
