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
    private String name; // 저축 상품명

    @Column(nullable = false, length = 50)
    private String bankName; // 은행명

    @Column(nullable = false, length = 20)
    private String bankCode; // 은행 코드

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavingProductType type; // 저축 상품 타입

    @Column(nullable = false)
    private Double interestRate; // 기본 금리

    @Column(nullable = false)
    private Integer periodMonth; // 가입 기간 개월 수

    @Column(nullable = false)
    private Long minAmount; // 최소 가입 금액

    private Long maxAmount; // 최대 가입 금액

    private Long monthlyLimit; // 월 납입 한도

    @Column(length = 1000)
    private String terms; // 가입 조건

    @Column(nullable = false)
    private boolean active; // 상품 활성 여부


}
