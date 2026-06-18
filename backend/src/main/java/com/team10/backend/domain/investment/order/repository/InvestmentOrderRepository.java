package com.team10.backend.domain.investment.order.repository;

import com.team10.backend.domain.investment.order.entity.InvestmentOrder;
import com.team10.backend.domain.investment.order.type.InvestmentOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentOrderRepository extends JpaRepository<InvestmentOrder, Long> {

    boolean existsByInvestmentAccountIdAndStatusIn(
            Long investmentAccountId,
            Collection<InvestmentOrderStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from InvestmentOrder o where o.id = :orderId")
    Optional<InvestmentOrder> findByIdForUpdate(Long orderId);

}
