package com.team10.backend.domain.account.service;

import com.team10.backend.domain.account.dto.req.AccountCreateReq;
import com.team10.backend.domain.account.dto.req.AccountNicknameUpdateReq;
import com.team10.backend.domain.account.dto.req.AccountPasswordChangeReq;
import com.team10.backend.domain.account.dto.req.AccountPasswordSetReq;
import com.team10.backend.domain.account.dto.res.AccountCreateRes;
import com.team10.backend.domain.account.dto.res.AccountDetailRes;
import com.team10.backend.domain.account.dto.res.AccountPasswordRes;
import com.team10.backend.domain.account.dto.res.AccountSummaryRes;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.account.util.AccountNumberGenerator;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private static final int MAX_ACCOUNT_NUMBER_GENERATION_RETRY = 10;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountCreateRes createAccount(Long userId, AccountCreateReq request) {
        validateGeneralAccountType(request.accountType());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.USER_NOT_FOUND));

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

        return AccountCreateRes.from(savedAccount);
    }

    @Transactional
    public AccountDetailRes updateNickname(Long userId, Long accountId, AccountNicknameUpdateReq request) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new
                        BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        account.updateNickname(request.nickname());

        return AccountDetailRes.from(account);
    }

    @Transactional
    public AccountPasswordRes setPassword(
            Long userId,
            Long accountId,
            AccountPasswordSetReq request
    ) {
        Account account = accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (account.getAccountPasswordHash() != null) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_ALREADY_SET);
        }

        account.changePassword(passwordEncoder.encode(request.accountPassword()));

        return AccountPasswordRes.from(account);
    }

    @Transactional
    public AccountPasswordRes changePassword(
            Long userId,
            Long accountId,
            AccountPasswordChangeReq request
    ) {
        Account account = accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_SAME);
        }

        account.verifyPassword(passwordEncoder, request.currentPassword());

        account.changePassword(passwordEncoder.encode(request.newPassword()));

        return AccountPasswordRes.from(account);
    }

    @Transactional
    public AccountDetailRes closeAccount(Long userId, Long accountId) {
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

        return AccountDetailRes.from(account);
    }

    public List<AccountSummaryRes> getAccounts(Long userId) {
        return accountRepository.findAllByUserIdAndStatusNot(userId, AccountStatus.CLOSED).stream()
                .map(AccountSummaryRes::from)
                .toList();
    }

    public List<AccountSummaryRes> getClosedAccounts(Long userId) {
        return accountRepository.findAllByUserIdAndStatus(userId, AccountStatus.CLOSED).stream()
                .map(AccountSummaryRes::from)
                .toList();
    }

    public AccountDetailRes getAccount(Long userId, Long accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        return AccountDetailRes.from(account);
    }

    private void validateGeneralAccountType(AccountType accountType) {
        if (accountType != AccountType.DEPOSIT) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }
    }

    private String generateUniqueAccountNumber() {
        for (int i = 0; i < MAX_ACCOUNT_NUMBER_GENERATION_RETRY; i++) {
            String accountNumber = AccountNumberGenerator.generate();

            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }

        throw new BusinessException(AccountErrorCode.ACCOUNT_NUMBER_GENERATION_FAILED);
    }
}
