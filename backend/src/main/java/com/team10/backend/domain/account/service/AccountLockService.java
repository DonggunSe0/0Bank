package com.team10.backend.domain.account.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final AccountRepository accountRepository;

    public Account findByIdForUpdate(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
    }

    public LockedAccounts lockTwoAccounts(Account firstAccount, Account secondAccount) {
        validateLockTargets(firstAccount, secondAccount);

        Long firstAccountId = firstAccount.getId();
        Long secondAccountId = secondAccount.getId();

        if (firstAccountId.compareTo(secondAccountId) < 0) {
            Account lockedFirstAccount = findByIdForUpdate(firstAccountId);
            Account lockedSecondAccount = findByIdForUpdate(secondAccountId);
            return new LockedAccounts(lockedFirstAccount, lockedSecondAccount);
        }

        Account lockedSecondAccount = findByIdForUpdate(secondAccountId);
        Account lockedFirstAccount = findByIdForUpdate(firstAccountId);
        return new LockedAccounts(lockedFirstAccount, lockedSecondAccount);
    }

    private void validateLockTargets(Account firstAccount, Account secondAccount) {
        if (firstAccount == null || secondAccount == null) {
            throw new IllegalArgumentException("Accounts to lock must not be null");
        }

        Long firstAccountId = firstAccount.getId();
        Long secondAccountId = secondAccount.getId();

        if (firstAccountId == null || secondAccountId == null) {
            throw new IllegalArgumentException("Account ID must not be null");
        }

        if (firstAccountId.equals(secondAccountId)) {
            throw new IllegalArgumentException("Cannot lock the same account twice: " + firstAccountId);
        }
    }

    public record LockedAccounts(Account firstAccount, Account secondAccount) {
    }
}
