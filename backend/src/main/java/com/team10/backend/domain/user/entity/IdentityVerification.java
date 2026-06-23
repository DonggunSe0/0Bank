package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.VerificationStatus;
import com.team10.backend.global.crypto.HmacHasher;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 다단계 본인인증 세션을 추적하는 엔티티(OCR → 행안부 검증 → 1원 송금). */
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

    /** OCR 주민등록번호 마스킹 표시값(앞 6자리만 노출). */
    @Column(length = 20)
    private String ocrResidentNumber;

    /**
     * 주민등록번호 전체 원문에 대한 {@link HmacHasher} 단방향 해시 (Base64).
     * 복호화는 불가능하며, 추후 중복 인증 탐지 등 동등 비교가 필요할 때만 사용한다.
     */
    @Column(length = 64)
    private String ocrResidentNumberHash;

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

    /** OCR 결과 기록 및 다음 단계 전환. 주민번호는 마스킹 표시값+단방향 해시만 저장한다. */
    public void completeOcr(String name, String residentNumber, String issueDate, String residentNumberHash) {
        this.ocrName = name;
        this.ocrResidentNumber = mask(residentNumber);
        this.ocrResidentNumberHash = residentNumberHash;
        this.ocrIssueDate = issueDate;
        this.status = VerificationStatus.OCR_COMPLETED;
    }

    /** 앞 6자리만 남기고 나머지는 가린다 (하이픈 없는 값/null은 그대로 둔다) */
    private static String mask(String residentNumber) {
        if (residentNumber == null || !residentNumber.contains("-")) {
            return residentNumber;
        }
        String front = residentNumber.split("-")[0];
        return front + "-*******";
    }

    /** 행안부 진위 확인 성공 — 3단계(1원 송금)로 전환 */
    public void completeGovernmentVerification() {
        this.status = VerificationStatus.GOVERNMENT_VERIFIED;
    }

    /**
     * 1원 송금 요청 접수 — 실제 송금은 트랜잭션 커밋 후 비동기로 처리된다.
     * 동기 응답 시점에는 아직 송금이 완료되지 않았으므로 {@link #startOneWon()}과 구분한다.
     */
    public void requestOneWonTransfer() {
        this.status = VerificationStatus.ONE_WON_IN_PROGRESS;
    }

    /** 1원 송금 요청 완료 — 코드 입력 대기 상태로 전환 (비동기 처리 성공 후 호출) */
    public void startOneWon() {
        this.status = VerificationStatus.ONE_WON_PENDING;
    }

    /** 비동기 1원 송금 실패 시 GOVERNMENT_VERIFIED로 복구(재시도 가능). */
    public void revertOneWonRequest(String reason) {
        this.failureReason = reason;
        this.status = VerificationStatus.GOVERNMENT_VERIFIED;
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
