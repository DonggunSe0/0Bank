package com.team10.backend.domain.transaction.entity;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "transaction_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = true)
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type; // 거래 원인 [ MVP 상에서는 계좌 이체 & 입금 ]

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private TransactionDirection direction; // 입급 | 출금

    @Column(nullable = false)
    private Long amount; // 거래액

    @Column(nullable = false)
    private Long balanceBefore; // 거래 전 잔액

    @Column(nullable = false)
    private Long balanceAfter; // 거래 후 잔액

    @Column(length = 30, nullable = true)
    private String counterpartyAccountNumber; // 이체 시점 상대 계좌에 대한 스냅샷

    @Column(length = 30)
    private String counterpartyName; // 이체 시점 상대에 대한 정보 - 기본적으로 상대방 User name 고려

    @Column(length = 255, nullable = true)
    private String memo; // User가 작성하는 거래 메모

    @Column(nullable = false)
    private LocalDateTime transactedAt; // mvp 수준에서는 createdAt 으로 대체 가능하나 추후 예약 이체 등의 기능 염두 시 필요

    public static TransactionHistory createDeposit(
            Account account,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = null;
        history.type = TransactionType.DEPOSIT;
        history.direction = TransactionDirection.IN;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = null;
        history.counterpartyName = null;
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }

    public static TransactionHistory createTransferOut(
            Account account,
            Transfer transfer,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String counterpartyAccountNumber,
            String counterpartyName,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = transfer;
        history.type = TransactionType.TRANSFER;
        history.direction = TransactionDirection.OUT;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = counterpartyAccountNumber;
        history.counterpartyName = counterpartyName;
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }

    public static TransactionHistory createTransferIn(
            Account account,
            Transfer transfer,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String counterpartyAccountNumber,
            String counterpartyName,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = transfer;
        history.type = TransactionType.TRANSFER;
        history.direction = TransactionDirection.IN;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = counterpartyAccountNumber;
        history.counterpartyName = counterpartyName;
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }

    public static TransactionHistory createExchange(
            Account account,
            TransactionDirection direction,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = null;
        history.type = TransactionType.EXCHANGE;
        history.direction = direction;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = null;
        history.counterpartyName = "환전 주문";
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }

    public static TransactionHistory createSavingDepositSignup(
            Account account,
            TransactionDirection direction,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        return createSavingHistory(
                account,
                TransactionType.SAVING_DEPOSIT_SIGNUP,
                direction,
                amount,
                balanceBefore,
                balanceAfter,
                memo,
                transactedAt
        );
    }

    public static TransactionHistory createSavingInstallmentSignup(
            Account account,
            TransactionDirection direction,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        return createSavingHistory(
                account,
                TransactionType.SAVING_INSTALLMENT_SIGNUP,
                direction,
                amount,
                balanceBefore,
                balanceAfter,
                memo,
                transactedAt
        );
    }

    public static TransactionHistory createSavingCancelRefund(
            Account account,
            TransactionDirection direction,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = null;
        history.type = TransactionType.SAVING_CANCEL_REFUND;
        history.direction = direction;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = null;
        history.counterpartyName = null;
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }

    public static TransactionHistory createSavingMaturityPayout(
            Account account,
            TransactionDirection direction,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = null;
        history.type = TransactionType.SAVING_MATURITY;
        history.direction = direction;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = null;
        history.counterpartyName = null;
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }

    public static TransactionHistory createInstallmentPayment(
            Account account,
            TransactionDirection direction,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = null;
        history.type = TransactionType.INSTALLMENT_PAYMENT;
        history.direction = direction;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = null;
        history.counterpartyName = null;
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }

    private static TransactionHistory createSavingHistory(
            Account account,
            TransactionType type,
            TransactionDirection direction,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = new TransactionHistory();
        history.account = account;
        history.transfer = null;
        history.type = type;
        history.direction = direction;
        history.amount = amount;
        history.balanceBefore = balanceBefore;
        history.balanceAfter = balanceAfter;
        history.counterpartyAccountNumber = null;
        history.counterpartyName = null;
        history.memo = memo;
        history.transactedAt = transactedAt;
        return history;
    }
}
