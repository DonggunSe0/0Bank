package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.UserStatus;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
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
