package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExAccountRepository extends JpaRepository<ExAccount, Long> {
    List<ExAccount> findAllByUserId(Long userId);

    Optional<ExAccount> findByIdAndUserId(Long id, Long userId);

    //같은 사용자 + 같은 기관 + 같은 계좌번호이면 같은 외부 계좌로 본다.
    Optional<ExAccount> findByUserIdAndOrganizationAndAccountNumber(
            Long userId,
            String organization,
            String accountNumber
    );
}
