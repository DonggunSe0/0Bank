package com.team10.backend.domain.account.service;

import com.team10.backend.domain.account.dto.req.AccountCreateReq;
import com.team10.backend.domain.account.dto.req.AccountNicknameUpdateReq;
import com.team10.backend.domain.account.dto.res.AccountRes;
import com.team10.backend.domain.account.dto.res.AccountSummaryRes;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.util.AccountNumberGenerator;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    @Transactional
    public AccountRes createAccount(Long userId, AccountCreateReq request) {
        User user = entityManager.find(User.class, userId);

        if (user == null) {
            throw new BusinessException(AccountErrorCode.USER_NOT_FOUND);
        }

        if (!Boolean.TRUE.equals(user.getIdentityVerified())) {
            throw new BusinessException(AccountErrorCode.IDENTITY_VERIFICATION_REQUIRED);
        }

        String accountNumber = generateUniqueAccountNumber();

        Account account = Account.create(
                user,
                accountNumber,
                request.nickname(),
                request.accountType()
        );

        Account savedAccount = accountRepository.save(account);

        return toAccountRes(savedAccount);
    }

    @Transactional
    public AccountRes updateNickname(Long userId, Long accountId, AccountNicknameUpdateReq request) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new
                        BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        account.updateNickname(request.nickname());

        return toAccountRes(account);
    }


    @Transactional
    public AccountRes closeAccount(Long userId, Long accountId) {
        Account account = accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
                .orElseThrow(() -> new
                        BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (!account.getBalance().equals(0L)) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_BALANCE_NOT_ZERO);
        }

        account.close();

        return toAccountRes(account);
    }


    public List<AccountSummaryRes> getAccounts(Long userId) {
        return accountRepository.findAllByUserIdAndStatusNot(userId, AccountStatus.CLOSED).stream()
                .map(this::toAccountSummaryRes)
                .toList();
    }

    public AccountRes getAccount(Long userId, Long accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        return toAccountRes(account);
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;

        do {
            accountNumber = AccountNumberGenerator.generate();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private AccountRes toAccountRes(Account account) {
        return new AccountRes(
                account.getId(),
                account.getAccountNumber(),
                account.getNickname(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private AccountSummaryRes toAccountSummaryRes(Account account) {
        return new AccountSummaryRes(
                account.getId(),
                account.getAccountNumber(),
                account.getNickname(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
