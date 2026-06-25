package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.exchange.dto.res.ExchangeOrderRes;
import com.team10.backend.domain.exchange.entity.ExchangeOrder;
import com.team10.backend.domain.exchange.entity.ExchangeQuote;
import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.entity.FxWalletLedger;
import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.repository.ExchangeOrderRepository;
import com.team10.backend.domain.exchange.repository.ExchangeQuoteRepository;
import com.team10.backend.domain.exchange.repository.FxWalletLedgerRepository;
import com.team10.backend.domain.exchange.repository.FxWalletRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import com.team10.backend.domain.exchange.type.ExchangeDirection;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExchangeBusinessService {

    private final AccountRepository accountRepository;
    private final FxWalletRepository fxWalletRepository;
    private final FxWalletLedgerRepository fxWalletLedgerRepository;
    private final ExchangeOrderRepository exchangeOrderRepository;
    private final ExchangeQuoteRepository exchangeQuoteRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExchangeOrderRes executeExchangeOrder(
            Long userId,
            Long exchangeQuoteId,
            Long krwAccountId,
            Long fxWalletId
    ) {
        // 사용자 확인
        User user = findUser(userId);

        // 견적 확인
        ExchangeQuote quote = getOwnedUsableQuote(userId, exchangeQuoteId);

        // 방향 결정
        ExchangeDirection direction = resolveDirection(quote);

        // 계좌/지갑 락 부여 및 검증
        ExchangeResources exchangeResources = lockAndValidateResources(userId, krwAccountId, fxWalletId, quote);

        Account krwAccount = exchangeResources.krwAccount;
        FxWallet fxWallet = exchangeResources.fxWallet;
        Long krwBalanceBefore = krwAccount.getBalance();    // 이전 원화 잔액 캡쳐
        BigDecimal fxBalanceBefore = fxWallet.getBalance(); // 이전 외화 잔액 캡쳐

        // 잔액 반영
        applyExchange(direction, quote, krwAccount, fxWallet);

        LocalDateTime completedAt = LocalDateTime.now();
        TransactionHistory transactionHistory = saveKrwTransactionHistory(
                direction,
                quote,
                krwAccount,
                krwBalanceBefore,
                completedAt
        );

        // 완료한 주문 저장
        ExchangeOrder order = saveCompletedOrder(
                user,
                quote,
                krwAccount,
                fxWallet,
                direction,
                transactionHistory,
                completedAt
        );
        saveFxWalletLedger(direction, quote, fxWallet, order, fxBalanceBefore);

        return ExchangeOrderRes.from(order);
    }

    private ExchangeDirection resolveDirection(ExchangeQuote quote) {
        return quote.getFromCurrency().getCurrencyCode() == CurrencyCode.KRW
                ? ExchangeDirection.KRW_TO_FOREIGN
                : ExchangeDirection.FOREIGN_TO_KRW;
    }

    private ExchangeQuote getOwnedUsableQuote(Long userId, Long exchangeQuoteId) {
        // 견적 존재 검증
        ExchangeQuote quote = exchangeQuoteRepository.findById(exchangeQuoteId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.EXCHANGE_QUOTE_NOT_FOUND));

        // 견적 소유자 검증
        if (!userId.equals(quote.getUser().getId())) {
            throw new BusinessException(ExchangeErrorCode.EXCHANGE_QUOTE_ACCESS_DENIED);
        }

        // 견적 재사용 검증
        if (exchangeOrderRepository.existsByExchangeQuote_Id(exchangeQuoteId)) {
            throw new BusinessException(ExchangeErrorCode.EXCHANGE_QUOTE_ALREADY_USED);
        }

        // 이미 만료된 견적인지 검증
        if (quote.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ExchangeErrorCode.EXCHANGE_QUOTE_EXPIRED);
        }

        return quote;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.USER_NOT_FOUND));
    }

    private ExchangeResources lockAndValidateResources(
            Long userId,
            Long krwAccountId,
            Long fxWalletId,
            ExchangeQuote quote
    ) {
        // 원화 계좌 -> 외화 지갑 순서로 락 획득
        Account krwAccount = accountRepository.findByIdAndUserIdForUpdate(krwAccountId, userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        FxWallet fxWallet = fxWalletRepository.findByIdAndUserIdForUpdate(fxWalletId, userId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.FX_WALLET_NOT_FOUND));

        validateExchangeResources(krwAccount, fxWallet, quote);

        return new ExchangeResources(krwAccount, fxWallet);
    }

    private void validateExchangeResources(Account krwAccount, FxWallet fxWallet, ExchangeQuote quote) {
        // 원화 지갑 & 외화 지갑 상태 확인
        if (!krwAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (!fxWallet.isActive()) {
            throw new BusinessException(ExchangeErrorCode.FX_WALLET_NOT_ACTIVE);
        }
        if (quote.getFromCurrency().getStatus() != CurrencyStatus.ACTIVE
                || quote.getToCurrency().getStatus() != CurrencyStatus.ACTIVE) {
            throw new BusinessException(ExchangeErrorCode.CURRENCY_NOT_SUPPORTED);
        }
        // 지갑 통화와 견적 외화 통화가 일치하는지 검증
        if (fxWallet.getCurrency().getCurrencyCode() != quote.getFxCurrency().getCurrencyCode()) {
            throw new BusinessException(ExchangeErrorCode.FX_WALLET_CURRENCY_MISMATCH);
        }
    }

    private void applyExchange(
            ExchangeDirection direction,
            ExchangeQuote quote,
            Account krwAccount,
            FxWallet fxWallet
    ) {
        // 원화 -> 외화
        if (direction == ExchangeDirection.KRW_TO_FOREIGN) {
            krwAccount.withdraw(toKrwLong(quote.getFromAmount()));  // 원화 계좌 차감
            fxWallet.deposit(quote.getExpectedToAmount());          // 외화 지갑 입금
            return;
        }

        // 외화 -> 원화
        fxWallet.withdraw(quote.getFromAmount());                   // 외화 지갑 차감
        krwAccount.deposit(toKrwLong(quote.getExpectedToAmount())); // 원화 계좌 입금
    }

    private ExchangeOrder saveCompletedOrder(
            User user,
            ExchangeQuote quote,
            Account krwAccount,
            FxWallet fxWallet,
            ExchangeDirection direction,
            TransactionHistory transactionHistory,
            LocalDateTime completedAt
    ) {
        try {
            // exchange_quote_id unique 위반이 해당 시점에 발생해서 try-catch 문으로 처리하도록 saveAndFlush 사용
            return exchangeOrderRepository.saveAndFlush(ExchangeOrder.createCompleted(
                    user,
                    quote,
                    krwAccount,
                    fxWallet,
                    direction,
                    transactionHistory,
                    completedAt
            ));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ExchangeErrorCode.EXCHANGE_QUOTE_ALREADY_USED);
        }

    }

    private TransactionHistory saveKrwTransactionHistory(
            ExchangeDirection direction,
            ExchangeQuote quote,
            Account krwAccount,
            Long balanceBefore,
            LocalDateTime transactedAt
    ) {
        return transactionHistoryRepository.save(TransactionHistory.createExchange(
                krwAccount,
                resolveKrwTransactionDirection(direction),
                resolveKrwTransactionAmount(direction, quote),
                balanceBefore,
                krwAccount.getBalance(),
                "환전",
                transactedAt
        ));
    }

    private TransactionDirection resolveKrwTransactionDirection(ExchangeDirection direction) {
        return direction == ExchangeDirection.KRW_TO_FOREIGN
                ? TransactionDirection.OUT
                : TransactionDirection.IN;
    }

    private Long resolveKrwTransactionAmount(ExchangeDirection direction, ExchangeQuote quote) {
        return direction == ExchangeDirection.KRW_TO_FOREIGN
                ? toKrwLong(quote.getFromAmount())
                : toKrwLong(quote.getExpectedToAmount());
    }

    private void saveFxWalletLedger(
            ExchangeDirection direction,
            ExchangeQuote quote,
            FxWallet fxWallet,
            ExchangeOrder order,
            BigDecimal balanceBefore
    ) {
        fxWalletLedgerRepository.save(FxWalletLedger.create(
                fxWallet,
                order,
                fxWallet.getCurrency(),
                resolveLedgerDirection(direction),
                resolveLedgerAmount(direction, quote),
                balanceBefore,
                fxWallet.getBalance(),
                order.getCompletedAt()
        ));
    }

    private TransactionDirection resolveLedgerDirection(ExchangeDirection direction) {
        return direction == ExchangeDirection.KRW_TO_FOREIGN
                ? TransactionDirection.IN
                : TransactionDirection.OUT;
    }

    private BigDecimal resolveLedgerAmount(ExchangeDirection direction, ExchangeQuote quote) {
        return direction == ExchangeDirection.KRW_TO_FOREIGN
                ? quote.getExpectedToAmount()
                : quote.getFromAmount();
    }

    private Long toKrwLong(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(ExchangeErrorCode.INVALID_EXCHANGE_AMOUNT);
        }
    }

    private record ExchangeResources(
            Account krwAccount,
            FxWallet fxWallet
    ) {
    }
}
