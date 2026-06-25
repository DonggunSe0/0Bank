package com.team10.backend.domain.codef.exAccount.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "codef.connected-id-crypto")
public record CodefConnectedIdCryptoProperties(
        @NotBlank String secretKey,
        @NotBlank String keyVersion
) {

    @Override
    public String toString() {
        return "CodefConnectedIdCryptoProperties[secretKey=<redacted>, keyVersion=" + keyVersion + "]";
    }
}
