package com.team10.backend.domain.investment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KisProperties.class)
public class InvestmentConfig {
}
