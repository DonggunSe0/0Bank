package com.team10.backend.domain.investment.portfolio.service;

import com.team10.backend.domain.investment.account.repository.InvestmentAccountRepository;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.portfolio.dto.res.InvestmentHoldingRes;
import com.team10.backend.domain.investment.portfolio.repository.InvestmentHoldingRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentPortfolioService {

    private final InvestmentAccountRepository investmentAccountRepository;
    private final InvestmentHoldingRepository investmentHoldingRepository;

    public Page<InvestmentHoldingRes> getHoldings(Long userId, Long accountId, int page, int size) {
        investmentAccountRepository
                .findByIdAndUserIdAndStatusNot(accountId, userId, InvestmentAccountStatus.CLOSED)
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND));

        return investmentHoldingRepository
                .findPageByInvestmentAccountIdWithStock(accountId, PageRequest.of(page, size))
                .map(InvestmentHoldingRes::from);
    }
}
