package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transfer.dto.res.DepositRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.errorcode.TransferErrorCode;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    @Transactional
    public DepositRes deposit(Long accountId, Long amount, String memo) {
        Long loginUserId = 1L;

        // amount 1원 이상 확인
        if(amount == null || amount < 1L) throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);

        // accountId로 계좌 조회 & 로그인 사용자 소유 계좌인지 확인
        Account account = accountRepository.findByIdAndUserId(accountId, loginUserId).orElseThrow(
                () -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND)
        );

        // 계좌 상태 ACTIVE 확인
        if (!account.isActive()) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_ACTIVE);
        }

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

        // DepositRes 반환
        return new DepositRes(
                savedHistory.getId(),           // 거래내역ID
                account.getId(),                // 입금 대상 계좌ID
                savedHistory.getType(),         // 거래유형 (입금)
                savedHistory.getAmount(),       // 입금액
                savedHistory.getBalanceBefore(),// 입금 전 잔액
                savedHistory.getBalanceAfter(), // 입금 후 잔액
                savedHistory.getMemo(),         // 입금 메모
                savedHistory.getTransactedAt()  // 거래 발생 시각
        );
    }

    @Transactional
    public TransferRes transfer(Long senderAccountId, String receiverAccountNumber, Long amount, String memo) {
        Long loginUserId = 1L;

        // amount 1원 이상 확인
        if(amount == null || amount < 1L) throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);

        // senderAccountId로 출금 계좌 조회 & 출금 계좌가 로그인 사용자 소유인지 확인
        Account senderAccount = accountRepository.findByIdAndUserId(senderAccountId, loginUserId).orElseThrow(
                () -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND)
        );

        // receiverAccountNumber로 수취 계좌 조회
        Account receiverAccount = accountRepository.findByAccountNumber(receiverAccountNumber).orElseThrow(
                () -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND)
        );

        // 예외 처리: 입출금 계좌 동일한 경우
        if (senderAccount.getId().equals(receiverAccount.getId())) {
            throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);
        }

        // 두 계좌 모두 ACTIVE인지 확인
        if (!senderAccount.isActive() || !receiverAccount.isActive()) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 이전 잔액 캡쳐
        Long senderBalanceBefore = senderAccount.getBalance();
        Long receiverBalanceBefore = receiverAccount.getBalance();

        // 출금 계좌 잔액 충분한지 확인 -> account.withdraw() 내부에 확인 로직 구현
        senderAccount.withdraw(amount); // 출금 계좌 balance 감소
        receiverAccount.deposit(amount); // 수취 계좌 balance 증가

        // Transfer(SUCCESS) 저장
        Transfer transfer = Transfer.success(senderAccount, receiverAccount, amount, memo);

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

        // TransferRes 반환
        return new TransferRes(
                transfer.getId(),
                transfer.getStatus(),
                senderAccount.getId(),
                senderAccount.getAccountNumber(),
                receiverAccount.getAccountNumber(),
                amount,
                senderAccount.getBalance(),
                memo,
                transferredAt
        );
    }
}
