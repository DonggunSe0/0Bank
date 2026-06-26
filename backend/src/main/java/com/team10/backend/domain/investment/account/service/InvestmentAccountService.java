package com.team10.backend.domain.investment.account.service;

import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCloseReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCreateReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountUpdateReq;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCloseRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCreateRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountDetailRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountSummaryRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountUpdateRes;
import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.repository.InvestmentAccountRepository;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.account.util.InvestmentAccountNumberGenerator;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.portfolio.repository.InvestmentHoldingRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentAccountService {

    private static final int MAX_ACCOUNT_NUMBER_GENERATION_RETRY = 10;
    private static final Long INITIAL_CASH_BALANCE = 5_000_000L;

    private final InvestmentAccountRepository investmentAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvestmentHoldingRepository investmentHoldingRepository;

    public List<InvestmentAccountSummaryRes> getAccounts(Long userId) {
        return investmentAccountRepository.findAllByUserIdAndStatusNot(userId, InvestmentAccountStatus.CLOSED).stream()
                .map(InvestmentAccountSummaryRes::from)
                .toList();
    }

    public InvestmentAccountDetailRes getAccount(Long userId, Long accountId) {
        InvestmentAccount account = investmentAccountRepository
                .findByIdAndUserIdAndStatusNot(accountId, userId, InvestmentAccountStatus.CLOSED)
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND));

        return InvestmentAccountDetailRes.from(account);
    }

    @Transactional
    public InvestmentAccountCreateRes createAccount(Long userId, InvestmentAccountCreateReq request) {
        User user = getVerifiedUser(userId);
        String accountNumber = generateUniqueAccountNumber();

        InvestmentAccount account = InvestmentAccount.create(
                user,
                accountNumber,
                request.nickname(),
                passwordEncoder.encode(request.accountPassword()),
                INITIAL_CASH_BALANCE,
                request.currencyCode()
        );

        InvestmentAccount savedAccount = investmentAccountRepository.save(account);
        return InvestmentAccountCreateRes.from(savedAccount);
    }

    @Transactional
    public InvestmentAccountUpdateRes updateAccount(
            Long userId,
            Long accountId,
            InvestmentAccountUpdateReq request
    ) {
        InvestmentAccount account = getActiveAccountForUpdate(userId, accountId);
        account.verifyPassword(passwordEncoder, request.pastPassword());

        if (request.nickname() == null && request.newPassword() == null) {
            throw new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_UPDATE_VALUE_REQUIRED);
        }
        if (request.nickname() != null) {
            account.updateNickname(request.nickname());
        }
        if (request.newPassword() != null) {
            account.changePassword(passwordEncoder.encode(request.newPassword()));
        }

        return InvestmentAccountUpdateRes.from(account);
    }

    @Transactional
    public InvestmentAccountCloseRes closeAccount(
            Long userId,
            Long accountId,
            InvestmentAccountCloseReq request
    ) {
        InvestmentAccount account = getActiveAccountForUpdate(userId, accountId);
        account.verifyPassword(passwordEncoder, request.accountPassword());
        validateClosable(account);
        account.close();

        return InvestmentAccountCloseRes.from(account);
    }

    private User getVerifiedUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIdentityVerified())) {
            throw new BusinessException(InvestmentErrorCode.IDENTITY_VERIFICATION_REQUIRED);
        }

        return user;
    }

    private String generateUniqueAccountNumber() {
        for (int i = 0; i < MAX_ACCOUNT_NUMBER_GENERATION_RETRY; i++) {
            String accountNumber = InvestmentAccountNumberGenerator.generate();

            if (!investmentAccountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }

        throw new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NUMBER_GENERATION_FAILED);
    }

    private InvestmentAccount getActiveAccountForUpdate(Long userId, Long accountId) {
        InvestmentAccount account = investmentAccountRepository.findByIdAndUserIdForUpdate(accountId, userId)
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_ACTIVE);
        }

        return account;
    }

    private void validateClosable(InvestmentAccount account) {
        if (!account.getCashBalance().equals(0L)) {
            throw new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_CASH_BALANCE_NOT_ZERO);
        }

        if (investmentHoldingRepository.existsByInvestmentAccountId(account.getId())) {
            throw new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_HOLDING_EXISTS);
        }

    }
}
