package com.team10.backend.domain.exAccount.entity.value;

public record EncryptedConnectedId(
        String ciphertext,
        String iv,
        String keyVersion
) {

    @Override
    public String toString() {
        return "EncryptedConnectedId[ciphertext=<redacted>, iv=<redacted>, keyVersion="
                + keyVersion + "]";
    }
}
