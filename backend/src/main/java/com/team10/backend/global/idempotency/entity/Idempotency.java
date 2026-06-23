package com.team10.backend.global.idempotency.entity;

import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import com.team10.backend.global.idempotency.type.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_idempotency_key",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Idempotency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 50)
    private IdempotencyOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Lob
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private Idempotency(
            User user,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash
    ) {
        this.user = user;
        this.operationType = operationType;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.PROCESSING;
    }

    public static Idempotency processing(
            User user,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash
    ) {
        return new Idempotency(user, operationType, idempotencyKey, requestHash);
    }

    public void complete(String responseBody) {
        this.responseBody = responseBody;
        this.status = IdempotencyStatus.SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = IdempotencyStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = IdempotencyStatus.EXPIRED;
        this.completedAt = LocalDateTime.now();
    }
}
