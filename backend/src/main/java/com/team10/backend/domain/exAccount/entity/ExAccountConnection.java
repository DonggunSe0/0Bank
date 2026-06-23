package com.team10.backend.domain.exAccount.entity;

import com.team10.backend.domain.exAccount.type.ExAccountConnectionStatus;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "codef_external_account_connection",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_codef_connection_user_organization",
                columnNames = {"user_id", "organization"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExAccountConnection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String organization;

    @Column(name = "connected_id_ciphertext", nullable = false, length = 512)
    private String connectedIdCiphertext;

    @Column(name = "connected_id_iv", nullable = false, length = 32)
    private String connectedIdIv;

    @Column(name = "encryption_key_version", nullable = false, length = 50)
    private String encryptionKeyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExAccountConnectionStatus status;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    public static ExAccountConnection create(
            User user,
            String organization,
            EncryptedConnectedId encryptedConnectedId
    ) {
        ExAccountConnection connection = new ExAccountConnection();
        connection.user = user;
        connection.organization = organization;
        connection.replaceConnectedId(encryptedConnectedId);
        return connection;
    }

    public void replaceConnectedId(EncryptedConnectedId encryptedConnectedId) {
        this.connectedIdCiphertext = encryptedConnectedId.ciphertext();
        this.connectedIdIv = encryptedConnectedId.iv();
        this.encryptionKeyVersion = encryptedConnectedId.keyVersion();
        this.status = ExAccountConnectionStatus.ACTIVE;
        this.lastSyncedAt = null;
    }

    public EncryptedConnectedId encryptedConnectedId() {
        return new EncryptedConnectedId(
                connectedIdCiphertext,
                connectedIdIv,
                encryptionKeyVersion
        );
    }

    public void markSynced(LocalDateTime syncedAt) {
        this.lastSyncedAt = syncedAt;
    }

    public void requireReauthentication() {
        this.status = ExAccountConnectionStatus.REAUTH_REQUIRED;
    }

    public void revoke() {
        this.status = ExAccountConnectionStatus.REVOKED;
    }

    public boolean isActive() {
        return status == ExAccountConnectionStatus.ACTIVE;
    }
}
