package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.dto.req.ExAccountTransactionSyncReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRefreshRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExAccountTransactionService {

    private final ExAccountTransactionRepository transactionRepository;
    private final ExAccountRepository accountRepository;
    private final ExAccountService exAccountService;

    public List<ExAccountTransactionRes> getTransactions(Long userId) {
        return transactionRepository.findAllByExAccountUserIdOrderByTransactedAtDesc(userId).stream()
                .map(ExAccountTransactionRes::from)
                .toList();
    }

    @Transactional
    public ExAccountTransactionRefreshRes refreshTransactions(
            Long userId,
            Long exAccountId,
            List<ExAccountTransactionSyncReq> transactions
    ) {
        ExAccount account = accountRepository.findByIdAndUserId(exAccountId, userId)
                .orElseThrow(() -> new BusinessException(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND));

        validateTransactions(transactions);

        int createdCount = 0;
        int updatedCount = 0;

        for (ExAccountTransactionSyncReq transaction : transactions) {
            validateTransaction(transaction);

            if (upsertTransaction(account, transaction)) {
                createdCount++;
                continue;
            }

            updatedCount++;
        }

        transactions.stream()
                .map(ExAccountTransactionSyncReq::transactedAt)
                .max(Comparator.naturalOrder())
                .map(LocalDate::from)
                .ifPresent(account::updateLastTransactionAt);

        ExAccountDetailRes detail = exAccountService.getAccountDetail(userId, exAccountId);
        return ExAccountTransactionRefreshRes.of(transactions.size(), createdCount, updatedCount, detail);
    }

    private void validateTransactions(List<ExAccountTransactionSyncReq> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            throw new BusinessException(ExAccountErrorCode.EX_ACCOUNT_TRANSACTION_SYNC_ITEMS_REQUIRED);
        }
    }

    private void validateTransaction(ExAccountTransactionSyncReq transaction) {
        if (transaction == null
                || !hasText(transaction.transactionKey())
                || transaction.transactedAt() == null
                || transaction.direction() == null
                || transaction.amount() == null) {
            throw new BusinessException(ExAccountErrorCode.EX_ACCOUNT_TRANSACTION_SYNC_REQUIRED_FIELD_MISSING);
        }
    }

    private boolean upsertTransaction(ExAccount account, ExAccountTransactionSyncReq request) {
        ExAccountTransaction transaction = transactionRepository
                .findByExAccountIdAndTransactionKey(account.getId(), request.transactionKey())
                .orElse(null);

        if (transaction == null) {
            transactionRepository.save(request.toEntity(account));
            return true;
        }

        request.applyTo(transaction);
        return false;
    }
}
