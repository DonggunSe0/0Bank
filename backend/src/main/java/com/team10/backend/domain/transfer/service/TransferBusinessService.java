package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.event.TransferFailedEvent;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransferBusinessService {

    private final AccountRepository accountRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final TransferRepository transferRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TopUpRes executeTopUp(Long userId, Long accountId, Long amount, String memo) {
        // amount 1원 이상 확인
        if (amount == null || amount < 1L) throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);

        // accountId로 계좌 조회 & 로그인 사용자 소유 계좌인지 확인 -> 비관적락
        Account account = accountRepository.findByIdAndUserIdForUpdate(accountId, userId).orElseThrow(
                () -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND)
        );

        // 계좌 상태 ACTIVE 확인
        if (!account.isActive()) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 일반 입금 대상 계좌도 입출금계좌만 허용
        validateReceiverCanTransferIn(account);

        // 입금 전 잔액 캡쳐
        Long balanceBefore = account.getBalance();
        // 입금
        account.deposit(amount);

        // TransactionHistory(DEPOSIT, IN) 저장
        LocalDateTime transactedAt = LocalDateTime.now();

        TransactionHistory transactionHistory = TransactionHistory.createDeposit(
                account,
                amount,
                balanceBefore,
                account.getBalance(),
                memo,
                transactedAt
        );

        TransactionHistory savedHistory = transactionHistoryRepository.save(transactionHistory);

        return TopUpRes.from(savedHistory);
    }

    @Transactional
    public TransferRes executeTransfer(
            Long userId,
            Long senderAccountId,
            String receiverAccountNumber,
            String accountPassword,
            Long amount, String memo
    ) {

        // amount 1원 이상 확인
        if (amount == null || amount < 1L) throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);

        // 락 획득
        TransferBusinessService.LockedTransferAccounts accounts =
                lockTransferAccounts(senderAccountId, receiverAccountNumber);

        Account senderAccount = accounts.sender();
        Account receiverAccount = accounts.receiver();

        // 출금 계좌가 로그인 유저의 소유인지 확인
        validateSenderOwner(senderAccount, userId);
        // 서로 다른 계좌인지 확인
        validateDifferentAccounts(senderAccount, receiverAccount);
        // 두 계좌 모두 ACTIVE인지 확인
        validateAccountsActive(senderAccount, receiverAccount);
        // 일반 송금 출금 계좌는 입출금계좌만 허용
        validateSenderCanTransferOut(senderAccount);
        // 일반 송금 수취 계좌도 입출금계좌만 허용
        validateReceiverCanTransferIn(receiverAccount);
        // 출금 계좌 비밀번호 일치 여부 확인
        validateAccountPassword(senderAccount, accountPassword);

        // 이전 잔액 캡쳐
        Long senderBalanceBefore = senderAccount.getBalance();
        Long receiverBalanceBefore = receiverAccount.getBalance();

        // 출금 계좌 잔액 충분한지 확인 -> account.withdraw() 내부에 확인 로직 구현
        try {
            senderAccount.withdraw(amount);
        } catch (BusinessException e) {
            if (e.getErrorCode() == TransferErrorCode.INSUFFICIENT_BALANCE) {
                publishTransferFailedEvent(senderAccount.getId(), receiverAccount.getId(), amount, memo);
            }
            throw e;
        }
        receiverAccount.deposit(amount); // 수취 계좌 balance 증가

        // Transfer(SUCCESS) 저장
        Transfer transfer = transferRepository.save(Transfer.success(senderAccount, receiverAccount, amount, memo));
        LocalDateTime transferredAt = LocalDateTime.now();
        // 출금 계좌 TransactionHistory(TRANSFER, OUT) 저장
        TransactionHistory senderHistory = TransactionHistory.createTransferOut(
                senderAccount,
                transfer,
                amount,
                senderBalanceBefore,
                senderAccount.getBalance(),
                receiverAccount.getAccountNumber(),
                receiverAccount.getUser().getName(),
                memo,
                transferredAt
        );

        // 수취 계좌 TransactionHistory(TRANSFER, IN) 저장
        TransactionHistory receiverHistory = TransactionHistory.createTransferIn(
                receiverAccount,
                transfer,
                amount,
                receiverBalanceBefore,
                receiverAccount.getBalance(),
                senderAccount.getAccountNumber(),
                senderAccount.getUser().getName(),
                memo,
                transferredAt
        );

        transactionHistoryRepository.save(senderHistory);
        transactionHistoryRepository.save(receiverHistory);

        return TransferRes.from(transfer, transferredAt);
    }

    private void validateAccountPassword(Account senderAccount, String accountPassword) {
        try {
            senderAccount.verifyPassword(passwordEncoder, accountPassword);
        } catch (BusinessException e) {
            if (e.getErrorCode() == AccountErrorCode.ACCOUNT_PASSWORD_NOT_SET) {
                throw new BusinessException(TransferErrorCode.ACCOUNT_PASSWORD_NOT_SET);
            }

            if (e.getErrorCode() == AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH) {
                throw new BusinessException(TransferErrorCode.ACCOUNT_PASSWORD_MISMATCH);
            }

            throw e;
        }
    }

    private LockedTransferAccounts lockTransferAccounts(
            Long senderAccountId,
            String receiverAccountNumber
    ) {
        // receiverAccountNumber로 수취 계좌 ID 조회
        Long receiverAccountId = accountRepository.findIdByAccountNumber(receiverAccountNumber)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND));

        validateDifferentAccountIds(senderAccountId, receiverAccountId);

        Long firstId = Math.min(senderAccountId, receiverAccountId);
        Long secondId = Math.max(senderAccountId, receiverAccountId);

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND));

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND));

        Account senderAccount = first.getId().equals(senderAccountId) ? first : second;
        Account receiverAccount = first.getId().equals(receiverAccountId) ? first : second;

        return new LockedTransferAccounts(senderAccount, receiverAccount);
    }

    private record LockedTransferAccounts(
            Account sender,
            Account receiver
    ) {
    }

    private void validateDifferentAccounts(Account senderAccount, Account receiverAccount) {
        if (senderAccount.getId().equals(receiverAccount.getId())) {
            throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateDifferentAccountIds(Long senderAccountId, Long receiverAccountId) {
        if (senderAccountId.equals(receiverAccountId)) {
            throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateAccountsActive(Account senderAccount, Account receiverAccount) {
        if (!senderAccount.isActive() || !receiverAccount.isActive()) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    private void validateSenderCanTransferOut(Account senderAccount) {
        if (!senderAccount.getAccountType().canTransferOut()) {
            throw new BusinessException(TransferErrorCode.INVALID_SENDER_ACCOUNT_TYPE);
        }
    }

    private void validateReceiverCanTransferIn(Account receiverAccount) {
        if (receiverAccount.getAccountType() != AccountType.DEPOSIT) {
            throw new BusinessException(TransferErrorCode.INVALID_RECEIVER_ACCOUNT_TYPE);
        }
    }

    private void validateSenderOwner(Account senderAccount, Long loginUserId) {
        if (!senderAccount.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void publishTransferFailedEvent(
            Long senderAccountId,
            Long receiverAccountId,
            Long amount,
            String memo
    ) {
        eventPublisher.publishEvent(new TransferFailedEvent(senderAccountId, receiverAccountId, amount, memo));
    }

}
