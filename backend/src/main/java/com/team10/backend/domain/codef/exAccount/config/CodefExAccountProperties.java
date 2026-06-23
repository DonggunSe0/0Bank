package com.team10.backend.domain.codef.exAccount.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "codef.account-inquiry")
public record CodefExAccountProperties(
        @NotBlank String serviceType,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String publicKey,
        @NotBlank String baseUrl,
        @NotBlank String accountCreatePath,
        @NotBlank String accountListPath,
        @NotBlank String bankTransactionPath
) {

    @Override
    public String toString() {
        return "CodefExAccountProperties[serviceType=" + serviceType
                + ", clientId=<redacted>"
                + ", clientSecret=<redacted>"
                + ", publicKey=<redacted>"
                + ", baseUrl=" + baseUrl
                + ", accountCreatePath=" + accountCreatePath
                + ", accountListPath=" + accountListPath
                + ", bankTransactionPath=" + bankTransactionPath
                + "]";
    }
}
