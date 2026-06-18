package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExAccountRepository extends JpaRepository<ExAccount, Long> {
    List<ExAccount> findAllByUserId(Long userId);
}