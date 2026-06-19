package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExAccountService {
    private final ExAccountRepository accountRepository;
    private final ExAccountTransactionRepository transactionRepository;

    public List<ExAccountRes> getAccounts(Long userId) {
        return accountRepository.findAllByUserId(userId).stream()
                .map(ExAccountRes::from)
                .toList();
    }

    public ExAccountDetailRes getAccountDetail(Long userId, Long exAccountId) {
        ExAccount account = accountRepository.findByIdAndUserId(exAccountId, userId)
                .orElseThrow(() -> new BusinessException(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND));

        return getAccountDetail(account, userId);
    }

    private ExAccountDetailRes getAccountDetail(ExAccount account, Long userId) {
        List<ExAccountTransactionRes> transactions = transactionRepository
                .findAllByExAccountIdAndExAccountUserIdOrderByTransactedAtDesc(account.getId(), userId)
                .stream()
                .map(ExAccountTransactionRes::from)
                .toList();

        return ExAccountDetailRes.of(ExAccountRes.from(account), transactions);
    }
}
