package com.team10.backend.domain.transfer.repository;

import com.team10.backend.domain.transfer.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
}
