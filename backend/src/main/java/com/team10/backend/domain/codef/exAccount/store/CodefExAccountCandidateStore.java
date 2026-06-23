package com.team10.backend.domain.codef.exAccount.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CodefExAccountCandidateStore {

    private static final String KEY_PREFIX = "codef:candidate:";
    private static final String CLAIM_KEY_PREFIX = "codef:candidate:claim:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration CLAIM_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    @Qualifier("getAndDeleteIfMatchScript")
    private final RedisScript<Long> getAndDeleteIfMatchScript;

    public String save(Long userId, List<CodefExAccountSnapshot> snapshots) {
        String token = UUID.randomUUID().toString();
        String key = redisKey(userId, token);
        try {
            String value = objectMapper.writeValueAsString(snapshots);
            redisTemplate.opsForValue().set(key, value, TTL);
            return token;
        } catch (JsonProcessingException e) {
            throw new CodefExAccountClientException("임시 계좌 후보 정보 저장에 실패했습니다.", e);
        }
    }

    public List<CodefExAccountSnapshot> get(Long userId, String token) {
        String key = redisKey(userId, token);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<CodefExAccountSnapshot>>() {});
        } catch (JsonProcessingException e) {
            throw new CodefExAccountClientException("임시 계좌 후보 정보를 해석하는 데 실패했습니다.", e);
        }
    }

    public void remove(Long userId, String token) {
        String key = redisKey(userId, token);
        redisTemplate.delete(key);
    }

    public Optional<String> claim(Long userId, String token) {
        String claimId = UUID.randomUUID().toString();
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(claimKey(userId, token), claimId, CLAIM_TTL);
        if (Boolean.TRUE.equals(claimed)) {
            return Optional.of(claimId);
        }
        return Optional.empty();
    }

    public void releaseClaim(Long userId, String token, String claimId) {
        redisTemplate.execute(
                getAndDeleteIfMatchScript,
                List.of(claimKey(userId, token)),
                claimId
        );
    }

    private String redisKey(Long userId, String token) {
        return KEY_PREFIX + userId + ":" + token;
    }

    private String claimKey(Long userId, String token) {
        return CLAIM_KEY_PREFIX + userId + ":" + token;
    }
}
