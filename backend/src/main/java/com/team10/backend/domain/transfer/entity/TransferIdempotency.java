package com.team10.backend.domain.transfer.entity;

import com.team10.backend.domain.transfer.type.IdempotencyStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "transfer_idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_id_idempotency_key",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferIdempotency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = true) // 멱등성 레코드를 송금처리 전에 만들기때문에 nullable
    private Transfer transfer;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Lob
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    private TransferIdempotency(User user, String idempotencyKey, String requestHash) {
        this.user = user;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.PROCESSING;
    }

    public static TransferIdempotency processing(User user, String idempotencyKey, String requestHash) {
        return new TransferIdempotency(user, idempotencyKey, requestHash);
    }

    public void complete(Transfer transfer, String responseBody) {
        this.transfer = transfer;
        this.responseBody = responseBody;
        this.status = IdempotencyStatus.SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = IdempotencyStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
