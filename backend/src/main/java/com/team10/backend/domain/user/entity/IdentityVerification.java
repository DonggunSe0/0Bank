package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.VerificationStatus;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다단계 본인인증 세션을 추적하는 엔티티.
 *
 * 각 인증 시도는 하나의 레코드로 관리된다.
 * OCR → 행안부 검증 → 1원 송금 의 단계별 상태를 status 필드로 추적한다.
 */
@Getter
@Entity
@Table(name = "identity_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityVerification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** OCR로 추출한 이름 */
    @Column(length = 50)
    private String ocrName;

    /** OCR로 추출한 주민등록번호 (뒷자리 포함 원문 저장, 실운영에서는 암호화 필요) */
    @Column(length = 20)
    private String ocrResidentNumber;

    /** OCR로 추출한 발급일자 (yyyy-MM-dd 형식 정규화) */
    @Column(length = 20)
    private String ocrIssueDate;

    /** OCR 실패 또는 인증 실패 사유 */
    @Column(length = 255)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VerificationStatus status;

    @Builder
    private IdentityVerification(User user, VerificationStatus status) {
        this.user = user;
        this.status = status;
    }

    public static IdentityVerification startOcr(User user) {
        return IdentityVerification.builder()
                .user(user)
                .status(VerificationStatus.OCR_PENDING)
                .build();
    }

    /** OCR 파싱 성공 시 결과를 기록하고 다음 단계로 전환 */
    public void completeOcr(String name, String residentNumber, String issueDate) {
        this.ocrName = name;
        this.ocrResidentNumber = residentNumber;
        this.ocrIssueDate = issueDate;
        this.status = VerificationStatus.OCR_COMPLETED;
    }

    /** 행안부 진위 확인 성공 — 뒷자리 마스킹 후 3단계(1원 송금)로 전환 */
    public void completeGovernmentVerification() {
        if (this.ocrResidentNumber != null && this.ocrResidentNumber.contains("-")) {
            String front = this.ocrResidentNumber.split("-")[0];
            this.ocrResidentNumber = front + "-*******";
        }
        this.status = VerificationStatus.GOVERNMENT_VERIFIED;
    }

    /** 1원 송금 요청 완료 — 코드 입력 대기 상태로 전환 */
    public void startOneWon() {
        this.status = VerificationStatus.ONE_WON_PENDING;
    }

    /** 1원 송금 코드 검증 성공 — 인증 완료 */
    public void completeOneWon() {
        this.status = VerificationStatus.COMPLETED;
    }

    /** 인증 실패 처리 */
    public void fail(String reason) {
        this.failureReason = reason;
        this.status = VerificationStatus.FAILED;
    }
}
