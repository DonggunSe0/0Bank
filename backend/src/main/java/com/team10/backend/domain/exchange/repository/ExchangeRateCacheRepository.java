package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.dto.res.ExchangeRateRes;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ExchangeRateCacheRepository {

    private static final String KEY = "exchange-rate:latest";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public void saveAll(List<ExchangeRateRes> rates) {
        // Redis Hash에 저장
        rates.forEach(rate -> {
            String hashKey = rate.currencyCode().name();
            String value = serialize(rate);

            redisTemplate.opsForHash().put(KEY, hashKey, value);
        });
    }

    public void save(ExchangeRateRes rate) {
        String hashKey = rate.currencyCode().name();
        String value = serialize(rate);

        redisTemplate.opsForHash().put(KEY, hashKey, value);
    }

    // 직렬화 메서드
    private String serialize(ExchangeRateRes rate) {
        try {
            return objectMapper.writeValueAsString(rate);
        } catch (JacksonException e) {
            log.warn("환율 정보 JSON 직렬화 중 에러가 발생했습니다. currency={}", rate.currencyCode(), e);
            throw new IllegalStateException("환율 정보 직렬화에 실패했습니다.", e);
        }
    }

    // 역직렬화 메서드
    private Optional<ExchangeRateRes> deserialize(String value) {
        try {
            // JSON 문자열을 읽어서 ExchangeRateRes 타입의 객체로 변환
            return Optional.of(objectMapper.readValue(value, ExchangeRateRes.class));
        } catch (JacksonException e) {
            log.warn("환율 정보 JSON 역직렬화 중 에러가 발생했습니다. value={}", value, e);
            return Optional.empty();
        }
    }
    /*
    Redis Hash value 전체 조회
    -> String인 값만 통과
    -> String으로 변환
    -> JSON을 ExchangeRateRes로 변환
    -> 변환 실패한 Optional.empty 제거
    -> List로 반환
    */
    public List<ExchangeRateRes> findAll() {
        // Redis Hash 전체 조회
        return redisTemplate.opsForHash().values(KEY).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::deserialize)
                // Optional.empty() 제외하고 실제 값만 꺼냄 | Optional[USD] -> USD
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<ExchangeRateRes> findByCurrency(CurrencyCode currencyCode) {
        // Redis Hash에서 특정 통화 조회
        Object value = redisTemplate.opsForHash().get(KEY, currencyCode.name());

        if (value instanceof String json) {
            return deserialize(json);
        }

        return Optional.empty();
    }
}
