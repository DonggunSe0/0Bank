package com.team10.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// 타임 아웃 설정을 위해 RestTemplate을 직접 생성하여 스프링에 등록하는 설정 클래스
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 무한 대기 방지를 위한 읽기 타임아웃 설정 (5초)
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));

        // 설정을 적용하여 RestClient 생성 및 등록
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}