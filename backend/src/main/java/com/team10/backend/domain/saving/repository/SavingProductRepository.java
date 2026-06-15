package com.team10.backend.domain.saving.repository;

import com.team10.backend.domain.saving.entity.SavingProduct;
import com.team10.backend.domain.saving.type.SavingProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingProductRepository extends JpaRepository<SavingProduct, Long> {
   List<SavingProduct> findAllByTypeAndActiveTrue(SavingProductType type);

   Optional<SavingProduct> findByIdAndTypeAndActiveTrue(Long id, SavingProductType type);
}
