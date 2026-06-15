package com.team10.backend.domain.saving.entity;

import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "saving_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SavingProduct extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String bankName;

    @Column(nullable = false, length = 20)
    private String bankCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavingProductType type;

    @Column(nullable = false)
    private Double interestRate;

    @Column(nullable = false)
    private Integer periodMonth;

    @Column(nullable = false)
    private Long minAmount;

    private Long maxAmount;

    private Long monthlyLimit;

    @Column(length = 1000)
    private String terms;

    @Column(nullable = false)
    private boolean active;


}
