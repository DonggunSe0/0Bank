package com.team10.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpenApiConfig {

    @Value("${openapi.server-url:}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        String schemeName = "bearerAuth";

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("MoneyStory API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));

        if (StringUtils.hasText(serverUrl)) {
            openAPI.servers(List.of(new Server().url(serverUrl)));
        }

        return openAPI;
    }

    @Bean
    public GroupedOpenApi authUserApi() {
        return domainGroup("auth-user",
                "com.team10.backend.domain.user.controller");
    }

    @Bean
    public GroupedOpenApi accountApi() {
        return domainGroup("accounts",
                "com.team10.backend.domain.account.controller");
    }

    @Bean
    public GroupedOpenApi transactionApi() {
        return domainGroup("transactions",
                "com.team10.backend.domain.transaction.controller");
    }

    @Bean
    public GroupedOpenApi externalAccountApi() {
        return domainGroup("external-accounts",
                "com.team10.backend.domain.exAccount.controller");
    }

    @Bean
    public GroupedOpenApi transferApi() {
        return domainGroup("transfers",
                "com.team10.backend.domain.transfer.controller");
    }

    @Bean
    public GroupedOpenApi savingApi() {
        return domainGroup("savings",
                "com.team10.backend.domain.saving.controller");
    }

    @Bean
    public GroupedOpenApi exchangeApi() {
        return domainGroup("exchange",
                "com.team10.backend.domain.exchange.controller");
    }

    @Bean
    public GroupedOpenApi investmentApi() {
        return domainGroup("investment",
                "com.team10.backend.domain.investment.account.controller",
                "com.team10.backend.domain.investment.portfolio.controller",
                "com.team10.backend.domain.investment.realtime.controller",
                "com.team10.backend.domain.investment.stock.controller",
                "com.team10.backend.domain.investment.trade.controller",
                "com.team10.backend.domain.investment.watchlist.controller");
    }

    @Bean
    public GroupedOpenApi youngPolicyApi() {
        return domainGroup("youth-policies",
                "com.team10.backend.domain.youngPolicy.controller");
    }

    private GroupedOpenApi domainGroup(String group, String... packagesToScan) {
        return GroupedOpenApi.builder()
                .group(group)
                .packagesToScan(packagesToScan)
                .build();
    }
}
