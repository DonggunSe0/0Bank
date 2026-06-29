package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.UserStatus;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private Boolean identityVerified;

    @Column
    private LocalDateTime identityVerifiedAt;

    private static final long IDENTITY_VERIFICATION_VALID_DAYS = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Builder
    private User(String email, String password, String name,
                 String phoneNumber, LocalDate birthDate) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthDate = birthDate;
        this.identityVerified = false;
        this.status = UserStatus.ACTIVE;
    }

    public static User create(String email, String hashedPassword, String name,
                              String phoneNumber, LocalDate birthDate) {
        return User.builder()
                .email(email)
                .password(hashedPassword)
                .name(name)
                .phoneNumber(phoneNumber)
                .birthDate(birthDate)
                .build();
    }

    /** 본인인증 최종 완료 처리 */
    public void completeIdentityVerification() {
        this.identityVerified = true;
        this.identityVerifiedAt = LocalDateTime.now();
    }

    /**
     * 본인인증이 "현재" 유효한지 확인한다. 과거에 한 번이라도 완료했는지(identityVerified)가 아니라,
     * 완료 시점으로부터 {@value #IDENTITY_VERIFICATION_VALID_DAYS}일이 지나지 않았는지까지 함께 본다.
     * 만료된 경우 재인증(OCR부터 다시)이 필요하다는 의미로 false를 반환한다.
     */
    public boolean isIdentityVerificationValid() {
        return Boolean.TRUE.equals(identityVerified)
                && identityVerifiedAt != null
                && identityVerifiedAt.isAfter(LocalDateTime.now().minusDays(IDENTITY_VERIFICATION_VALID_DAYS));
    }

    /** 비밀번호 변경 */
    public void changePassword(String encodedNewPassword) {
        this.password = encodedNewPassword;
    }

    /** 회원 탈퇴 처리 */
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
    }
}
