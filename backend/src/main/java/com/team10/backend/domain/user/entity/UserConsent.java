package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.TermsType;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Entity
@Table(name = "user_consents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "terms_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType termsType;

    @Column(nullable = false)
    private Boolean agreed;

    @Column
    private LocalDateTime agreedAt;

    public static UserConsent of(User user, TermsType termsType, boolean agreed) {
        UserConsent consent = new UserConsent();
        consent.user = user;
        consent.termsType = termsType;
        consent.agreed = agreed;
        consent.agreedAt = agreed ? LocalDateTime.now(ZoneId.of("Asia/Seoul")) : null;
        return consent;
    }

    public void update(boolean agreed) {
        this.agreed = agreed;
        this.agreedAt = agreed ? LocalDateTime.now(ZoneId.of("Asia/Seoul")) : null;
    }
}
