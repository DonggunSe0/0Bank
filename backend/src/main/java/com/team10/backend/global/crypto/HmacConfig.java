package com.team10.backend.global.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link HmacProperties}를 빈으로 등록한다. ({@code KisProperties}/{@code InvestmentConfig}와 동일한 패턴)
 */
@Configuration
@EnableConfigurationProperties(HmacProperties.class)
public class HmacConfig {
}
