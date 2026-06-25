package com.team10.backend.domain.investment.account.entity;

import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@Getter
@Entity
@Table(
        name = "investment_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_investment_accounts_account_number", columnNames = "account_number")
        // indexes = @Index(name = "idx_investment_accounts_user_status", columnList = "user_id, status") 인덱스 추후 고려
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 13)
    private String accountNumber;

    @Column(length = 50)
    private String nickname;

    @Column(nullable = false, length = 255)
    private String accountPasswordHash;

    @Column(nullable = false)
    private Long cashBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvestmentAccountStatus status;

    public static InvestmentAccount create(
            User user,
            String accountNumber,
            String nickname,
            String accountPasswordHash,
            CurrencyCode currencyCode
    ) {
        InvestmentAccount account = new InvestmentAccount();
        account.user = user;
        account.accountNumber = accountNumber;
        account.nickname = nickname;
        account.accountPasswordHash = accountPasswordHash;
        account.cashBalance = 0L;
        account.currencyCode = currencyCode;
        account.status = InvestmentAccountStatus.ACTIVE;
        return account;
    }

    public void depositCash(Long amount) {
        validateAmount(amount);
        this.cashBalance += amount;
    }

    // TODO : 추구 구현하면서 JPQL 원자적 연산으로 전환 가능성 고려
    public void withdrawCash(Long amount) {
        validateAmount(amount);

        if (this.cashBalance < amount) {
            throw new BusinessException(InvestmentErrorCode.INSUFFICIENT_CASH_BALANCE);
        }
        this.cashBalance -= amount;
    }

    public boolean isActive() {
        return this.status == InvestmentAccountStatus.ACTIVE;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String accountPasswordHash) {
        this.accountPasswordHash = accountPasswordHash;
    }

    public void close() {
        this.status = InvestmentAccountStatus.CLOSED;
    }

    public void verifyPassword(PasswordEncoder encoder, String pastPassword) {
        if (!encoder.matches(pastPassword, accountPasswordHash)) {
            throw new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_PASSWORD_MISMATCH);
        }
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(
                    InvestmentErrorCode.INVALID_CASH_AMOUNT
            );
        }
    }
}
