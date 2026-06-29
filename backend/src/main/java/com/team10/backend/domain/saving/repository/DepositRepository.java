package com.team10.backend.domain.saving.repository;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.type.DepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

    @Query("""
          select d
          from Deposit d
          join fetch d.savingProduct
          where d.user.id = :userId
          order by d.createdAt desc
          """)
    List<Deposit> findAllByUserIdWithProduct(
            @Param("userId") Long userId
    );

    @Query("""
          select d
          from Deposit d
          join fetch d.savingProduct
          where d.user.id = :userId
          and d.status = :status
          order by d.createdAt desc
          """)
    List<Deposit> findAllByUserIdAndStatusWithProduct(
            @Param("userId") Long userId,
            @Param("status") DepositStatus status
    );

    @Query("""
        select d
        from Deposit d
        join fetch d.savingProduct
        where d.id = :depositId
        and d.user.id = :userId
        """)
    Optional<Deposit> findByIdAndUserIdWithProduct(
            @Param("depositId") Long depositId,
            @Param("userId") Long userId
    );

    @Query("""
      select d.id
      from Deposit d
      where d.status = :status
      and d.maturityDate <= :today
      """)
    List<Long> findIdsByStatusAndMaturityDateLessThanEqual(
            @Param("status") DepositStatus status,
            @Param("today") LocalDate today
    );

    @Query("""
        select d
        from Deposit d
        join fetch d.withdrawAccount
        join fetch d.savingAccount
        where d.id = :depositId
        """)
    Optional<Deposit> findByIdWithAccount(
            @Param("depositId") Long depositId
    );

    @Query("""
      select d
      from Deposit d
      join fetch d.withdrawAccount
      join fetch d.savingAccount
      where d.id = :depositId
      and d.user.id = :userId
      """)
    Optional<Deposit> findByIdAndUserIdWithAccount(
            @Param("depositId") Long depositId,
            @Param("userId") Long userId
    );
}
