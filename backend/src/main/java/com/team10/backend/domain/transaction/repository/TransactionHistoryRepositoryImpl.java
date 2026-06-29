package com.team10.backend.domain.transaction.repository;

import static com.team10.backend.domain.transaction.entity.QTransactionHistory.transactionHistory;
import static com.team10.backend.domain.transaction.service.TransactionHistoryService.SORT_PROPERTY_TRANSACTED_AT;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team10.backend.domain.transaction.dto.req.TransactionHistorySearchReq;
import com.team10.backend.domain.transaction.dto.res.TransactionHistorySearchRes;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class TransactionHistoryRepositoryImpl implements TransactionHistoryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<TransactionHistorySearchRes> search(
            Long accountId,
            TransactionHistorySearchReq filter,
            Pageable pageable
    ) {
        // where 절 생성 헬퍼 메서드
        BooleanBuilder condition = buildSearchCondition(accountId, filter);

        // controller 단의 요청파라미터 입력 시점에서 이미 default 값 지정되므로 non-null
        Sort.Direction direction = pageable.getSort()
                .getOrderFor(SORT_PROPERTY_TRANSACTED_AT)
                .getDirection();
        OrderSpecifier<LocalDateTime> orderSpecifier = direction.isAscending()
                ? transactionHistory.transactedAt.asc()
                : transactionHistory.transactedAt.desc();

        // 동적 쿼리 완성
        List<TransactionHistorySearchRes> content = queryFactory
                .select(Projections.constructor(
                        TransactionHistorySearchRes.class,
                        transactionHistory.id,
                        transactionHistory.type,
                        transactionHistory.counterpartyName,
                        Expressions.nullExpression(String.class),
                        transactionHistory.amount,
                        transactionHistory.balanceAfter,
                        transactionHistory.transactedAt,
                        transactionHistory.memo,
                        transactionHistory.direction
                ))
                .from(transactionHistory)
                .where(condition)
                .orderBy(orderSpecifier)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(transactionHistory.count())
                .from(transactionHistory)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private BooleanBuilder buildSearchCondition(
            Long accountId,
            TransactionHistorySearchReq filter
    ) {
        return new BooleanBuilder()
                .and(accountIdEq(accountId))
                .and(transactedAtGoe(filter.startDate()))
                .and(transactedAtLtEndDate(filter.endDate()))
                .and(directionEq(filter.direction()))
                .and(amountGoe(filter.minAmount()))
                .and(amountLoe(filter.maxAmount()))
                .and(counterpartyNameContains(filter.counterpartyName()));
    }

    private BooleanExpression accountIdEq(Long accountId) {
        return transactionHistory.account.id.eq(accountId);
    }

    private BooleanExpression transactedAtGoe(LocalDate startDate) {
        if (startDate == null) {
            return null;
        }
        return transactionHistory.transactedAt.goe(startDate.atStartOfDay());
    }

    private BooleanExpression transactedAtLtEndDate(LocalDate endDate) {
        if (endDate == null) {
            return null;
        }
        return transactionHistory.transactedAt.lt(endDate.plusDays(1).atStartOfDay());
    }

    private BooleanExpression directionEq(TransactionDirection direction) {
        if (direction == null) {
            return null;
        }
        return transactionHistory.direction.eq(direction);
    }

    private BooleanExpression amountGoe(Long minAmount) {
        if (minAmount == null) {
            return null;
        }
        return transactionHistory.amount.goe(minAmount);
    }

    private BooleanExpression amountLoe(Long maxAmount) {
        if (maxAmount == null) {
            return null;
        }
        return transactionHistory.amount.loe(maxAmount);
    }

    private BooleanExpression counterpartyNameContains(String counterpartyName) {
        if (!StringUtils.hasText(counterpartyName)) {
            return null;
        }
        return transactionHistory.counterpartyName.containsIgnoreCase(counterpartyName);
    }
}
