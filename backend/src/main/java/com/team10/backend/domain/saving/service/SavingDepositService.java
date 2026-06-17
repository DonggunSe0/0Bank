package com.team10.backend.domain.saving.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.saving.dto.req.DepositCreateReq;
import com.team10.backend.domain.saving.dto.res.DepositCreateRes;
import com.team10.backend.domain.saving.dto.res.DepositDetailRes;
import com.team10.backend.domain.saving.dto.res.DepositSummaryRes;
import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.SavingProduct;
import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.repository.DepositRepository;
import com.team10.backend.domain.saving.repository.SavingProductRepository;
import com.team10.backend.domain.saving.type.DepositStatus;
import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavingDepositService {
    private static final int MONTHS_IN_YEAR = 12;
    private static final int PERCENT_DIVISOR = 100;

    private final DepositRepository depositRepository;
    private final SavingProductRepository savingProductRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    public DepositCreateRes createDeposit(Long userId, DepositCreateReq request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.USER_NOT_FOUND));

        SavingProduct savingProduct = savingProductRepository
                .findByIdAndTypeAndActiveTrue(request.productId(),
                        SavingProductType.DEPOSIT)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));

        Account withdrawAccount = accountRepository
                .findByIdAndUserId(request.withdrawAccountId(), userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!withdrawAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (request.amount() < savingProduct.getMinAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
        }

        if (savingProduct.getMaxAmount() != null
                && request.amount() > savingProduct.getMaxAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
        }

        LocalDate maturityDate = LocalDate.now()
                .plusMonths(savingProduct.getPeriodMonth());

        withdrawAccount.withdraw(request.amount());

        // 원금 × 연이율 × 가입개월수 ÷ 1년 개월수 ÷ 퍼센트 변환값
        Long expectedInterest = (long) (
                request.amount()
                        * savingProduct.getInterestRate()
                        * savingProduct.getPeriodMonth()
                        / MONTHS_IN_YEAR
                        / PERCENT_DIVISOR
        );

        Deposit deposit = Deposit.create(
                user,
                savingProduct,
                withdrawAccount,
                request.amount(),
                savingProduct.getInterestRate(),
                maturityDate,
                expectedInterest
        );

        Deposit savedDeposit = depositRepository.save(deposit);

        return DepositCreateRes.from(savedDeposit);
    }

    public List<DepositSummaryRes> getDeposits(Long userId, DepositStatus status) {
        List<Deposit> deposits;

        if (status == null) {
            deposits = depositRepository.findAllByUserIdWithProduct(userId);
        } else {
            deposits = depositRepository.findAllByUserIdAndStatusWithProduct(userId, status);
        }

        return deposits.stream()
                .map(DepositSummaryRes::from)
                .toList();
    }

    public DepositDetailRes getDeposit(Long userId, Long depositId) {
        Deposit deposit =
                depositRepository.findByIdAndUserIdWithProduct(depositId, userId)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));

        return DepositDetailRes.from(deposit);
    }
}
