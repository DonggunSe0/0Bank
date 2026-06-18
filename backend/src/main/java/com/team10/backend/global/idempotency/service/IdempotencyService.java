package com.team10.backend.global.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.repository.IdempotencyRepository;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import com.team10.backend.global.idempotency.type.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");

    private final IdempotencyRepository idempotencyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> IdempotencyReserveResult<T> reserve(
            Long userId,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType // 저장된 JSON 응답을 다시 복원할 DTO 타입
    ) {
        validateIdempotencyKey(idempotencyKey);

        return idempotencyRepository
                .findByUser_IdAndOperationTypeAndIdempotencyKey(userId, operationType, idempotencyKey)
                .map(existing -> resolveExisting(existing, requestHash, responseType))
                .orElseGet(() -> createProcessing(userId, operationType, idempotencyKey, requestHash, responseType));
    }

    @Transactional
    public void completeSuccess(Long idempotencyId, Object response) {
        Idempotency idempotency = idempotencyRepository.findById(idempotencyId)
                .orElseThrow();

        idempotency.complete(serializeResponse(response));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFailure(Long idempotencyId) {
        Idempotency idempotency = idempotencyRepository.findById(idempotencyId)
                .orElseThrow();

        idempotency.fail();
    }

    @Transactional
    public int expireStaleProcessing(Duration timeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(timeout);
        List<Idempotency> staleRecords = idempotencyRepository.findStaleProcessing(threshold);

        staleRecords.forEach(Idempotency::expire);

        return staleRecords.size();
    }

    private <T> IdempotencyReserveResult<T> createProcessing(
            Long userId,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType
    ) {
        User user = userRepository.getReferenceById(userId);
        Idempotency idempotency = idempotencyRepository.saveAndFlush(
                Idempotency.processing(user, operationType, idempotencyKey, requestHash)
        );

        return IdempotencyReserveResult.reserved(idempotency);
    }

    private <T> IdempotencyReserveResult<T> resolveExisting(
            Idempotency idempotency,
            String requestHash,
            Class<T> responseType
    ) {
        if (!idempotency.getRequestHash().equals(requestHash)) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_CONFLICT);
        }

        if (idempotency.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_PROCESSING);
        }

        if (idempotency.getStatus() == IdempotencyStatus.SUCCESS) {
            return IdempotencyReserveResult.replay(
                    deserializeResponse(idempotency.getResponseBody(), responseType)
            );
        }

        if (idempotency.getStatus() == IdempotencyStatus.FAILED) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_FAILED);
        }

        if (idempotency.getStatus() == IdempotencyStatus.EXPIRED) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_EXPIRED);
        }

        throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_PROCESSING);
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
    }

    private String serializeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
    }

    private <T> T deserializeResponse(String responseBody, Class<T> responseType) {
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency response", e);
        }
    }
}
