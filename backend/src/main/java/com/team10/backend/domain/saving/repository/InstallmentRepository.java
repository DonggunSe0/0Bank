package com.team10.backend.domain.saving.repository;

import com.team10.backend.domain.saving.entity.Installment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {
}
