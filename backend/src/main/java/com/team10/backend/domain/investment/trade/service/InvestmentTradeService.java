package com.team10.backend.domain.investment.trade.service;

import com.team10.backend.domain.investment.trade.dto.req.MarketOrderCreateReq;
import com.team10.backend.domain.investment.trade.dto.res.InvestmentTradeRes;
import com.team10.backend.global.idempotency.annotation.Idempotent;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvestmentTradeService {

    private final InvestmentTradeBusinessService investmentTradeBusinessService;

    @Idempotent(
            operationType = IdempotencyOperationType.INVESTMENT_MARKET_ORDER,
            userId = "#userId",
            key = "#idempotencyKey",
            hashFields = {
                    "#request.accountId",
                    "#request.stockId",
                    "#request.tradeType",
                    "#request.quantity",
                    "#request.expectedPrice"
            }
    )
    public InvestmentTradeRes createMarketOrder(
            Long userId,
            String idempotencyKey,
            MarketOrderCreateReq request
    ) {
        return investmentTradeBusinessService.executeMarketOrder(userId, idempotencyKey, request);
    }
}
