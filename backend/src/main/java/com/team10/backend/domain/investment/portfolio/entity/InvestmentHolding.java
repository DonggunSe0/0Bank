package com.team10.backend.domain.investment.portfolio.entity;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.global.entity.BaseEntity;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "investment_holdings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_investment_holdings_account_stock",
                columnNames = {"investment_account_id", "stock_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentHolding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_account_id", nullable = false)
    private InvestmentAccount investmentAccount; // 주식 게좌

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock; // 주식 종목

    @Column(nullable = false)
    private Long quantity; // 수량

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal averagePrice; // 평균가

    public static InvestmentHolding create(
            InvestmentAccount investmentAccount,
            Stock stock,
            Long quantity,
            BigDecimal averagePrice
    ) {
        InvestmentHolding holding = new InvestmentHolding();
        holding.investmentAccount = investmentAccount;
        holding.stock = stock;
        holding.quantity = quantity;
        holding.averagePrice = averagePrice;
        return holding;
    }

    public void increase(Long quantity, Long executionPrice) {
        validateQuantity(quantity);
        validatePrice(executionPrice);

        BigDecimal currentAmount = averagePrice.multiply(BigDecimal.valueOf(this.quantity));
        BigDecimal addedAmount = BigDecimal.valueOf(executionPrice).multiply(BigDecimal.valueOf(quantity));
        long newQuantity;
        try {
            newQuantity = Math.addExact(this.quantity, quantity);
        } catch (ArithmeticException e) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_QUANTITY);
        }

        this.quantity = newQuantity;
        this.averagePrice = currentAmount.add(addedAmount)
                .divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);
    }

    public void decrease(Long quantity) {
        validateQuantity(quantity);
        if (this.quantity < quantity) {
            throw new BusinessException(InvestmentErrorCode.INSUFFICIENT_HOLDING_QUANTITY);
        }
        this.quantity -= quantity;
    }

    public boolean isEmpty() {
        return quantity == 0L;
    }

    private void validateQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_QUANTITY);
        }
    }

    private void validatePrice(Long executionPrice) {
        if (executionPrice == null || executionPrice <= 0) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_AMOUNT);
        }
    }
}
