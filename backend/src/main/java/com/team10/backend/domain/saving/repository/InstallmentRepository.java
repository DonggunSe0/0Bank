package com.team10.backend.domain.saving.repository;

import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    @Query("""
            select i
            from Installment i
            join fetch i.savingProduct
            where i.user.id = :userId
            order by i.createdAt desc
            """)
    List<Installment> findAllByUserIdWithProduct(
            @Param("userId") Long userId
    );

    @Query("""
            select i
            from Installment i
            join fetch i.savingProduct
            where i.user.id = :userId
            and i.status = :status
            order by i.createdAt desc
            """)
    List<Installment> findAllByUserIdAndStatusWithProduct(
            @Param("userId") Long userId,
            @Param("status") InstallmentStatus status
    );

    @Query("""
            select i
            from Installment i
            join fetch i.savingProduct
            where i.id = :installmentId
            and i.user.id = :userId
            """)
    Optional<Installment> findByIdAndUserIdWithProduct(
            @Param("installmentId") Long installmentId,
            @Param("userId") Long userId
    );

    @Query("""
        select i.id
        from Installment i
        where i.status = :status
        and i.maturityDate <= :today
        """)
    List<Long> findIdsByStatusAndMaturityDateLessThanEqual(
            @Param("status") InstallmentStatus status,
            @Param("today") LocalDate today
    );

    @Query("""
        select i.id
        from Installment i
        where i.status = :status
        and i.nextPaymentRetryDate <= :today
        """)
    List<Long> findRetryTargetIds(
            @Param("status") InstallmentStatus status,
            @Param("today") LocalDate today
    );

    @Query("""
        select i.id
        from Installment i
        where i.status = :status
        and i.autoTransferYn = true
        and i.nextPaymentDate <= :today
        and i.paidAmount < i.targetAmount
        and i.nextPaymentDate < i.maturityDate
        """)
    List<Long> findPaymentTargetIds(
            @Param("status") InstallmentStatus status,
            @Param("today") LocalDate today
    );

    @Query("""
        select i
        from Installment i
        join fetch i.withdrawAccount
        join fetch i.savingAccount
        where i.id = :installmentId
        """)
    Optional<Installment> findByIdWithAccount(
            @Param("installmentId") Long installmentId
    );

    @Query("""
          select i
          from Installment i
          join fetch i.withdrawAccount
          join fetch i.savingAccount
          where i.id = :installmentId
          and i.user.id = :userId
          """)
    Optional<Installment> findByIdAndUserIdWithAccount(
            @Param("installmentId") Long installmentId,
            @Param("userId") Long userId
    );
}
