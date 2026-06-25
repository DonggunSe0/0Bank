package com.team10.backend.domain.codef.exAccount.crypto;

import com.team10.backend.domain.codef.exAccount.config.CodefConnectedIdCryptoProperties;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountCryptoException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodefConnectedIdEncryptorTest {

    private static final String SECRET_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final CodefConnectedIdEncryptor encryptor = new CodefConnectedIdEncryptor(
            new CodefConnectedIdCryptoProperties(SECRET_KEY, "v1")
    );

    @Test
    void encryptsAndDecryptsConnectedIdWithAesGcm() {
        String connectedId = "3Lj7J-OvQ-sensitive";

        EncryptedConnectedId encrypted = encryptor.encrypt(connectedId);

        assertThat(encrypted.ciphertext()).doesNotContain(connectedId);
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(encrypted.keyVersion()).isEqualTo("v1");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(connectedId);
        assertThat(encrypted.toString()).doesNotContain(connectedId, encrypted.ciphertext(), encrypted.iv());
    }

    @Test
    void usesDifferentIvForEveryEncryption() {
        EncryptedConnectedId first = encryptor.encrypt("same-connected-id");
        EncryptedConnectedId second = encryptor.encrypt("same-connected-id");

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void rejectsTamperedCiphertext() {
        EncryptedConnectedId encrypted = encryptor.encrypt("connected-id");
        byte[] tamperedBytes = Base64.getDecoder().decode(encrypted.ciphertext());
        tamperedBytes[0] ^= 1;
        EncryptedConnectedId tampered = new EncryptedConnectedId(
                Base64.getEncoder().encodeToString(tamperedBytes),
                encrypted.iv(),
                encrypted.keyVersion()
        );

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(CodefExAccountCryptoException.class)
                .hasMessage("암호화된 connectedId 검증에 실패했습니다.");
    }

    @Test
    void rejectsUnsupportedKeyVersion() {
        EncryptedConnectedId encrypted = encryptor.encrypt("connected-id");
        EncryptedConnectedId oldVersion = new EncryptedConnectedId(
                encrypted.ciphertext(), encrypted.iv(), "v0"
        );

        assertThatThrownBy(() -> encryptor.decrypt(oldVersion))
                .isInstanceOf(CodefExAccountCryptoException.class)
                .hasMessage("지원하지 않는 connectedId 암호화 키 버전입니다.");
    }

    @Test
    void rejectsInvalidKeyLengthAtStartup() {
        String shortKey = Base64.getEncoder().encodeToString("short-key".getBytes());

        assertThatThrownBy(() -> new CodefConnectedIdEncryptor(
                new CodefConnectedIdCryptoProperties(shortKey, "v1")))
                .isInstanceOf(CodefExAccountCryptoException.class)
                .hasMessage("connectedId 암호화 키 길이가 올바르지 않습니다.")
                .hasMessageNotContaining(shortKey);
    }
}
