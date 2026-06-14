package com.team10.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// 타임 아웃 설정을 위해 RestTemplate을 직접 생성하여 스프링에 등록하는 설정 클래스
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        // 무한 대기 방지를 위한 타임아웃 설정 (5초)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        // 설정을 적용하여 RestClient 생성 및 등록
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}