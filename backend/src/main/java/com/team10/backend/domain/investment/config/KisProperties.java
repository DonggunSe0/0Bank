package com.team10.backend.domain.investment.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kis")
public record KisProperties(
        @NotBlank String appKey,
        @NotBlank String appSecret,
        @NotNull @Min(1) Integer websocketMaxSubscriptions
) {
}
