package com.team10.backend.domain.saving.repository;

import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
