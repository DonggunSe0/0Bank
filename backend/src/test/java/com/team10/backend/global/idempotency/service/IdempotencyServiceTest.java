package com.team10.backend.global.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.repository.IdempotencyRepository;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import com.team10.backend.global.idempotency.type.IdempotencyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("새 멱등성 키면 PROCESSING 레코드를 선점한다")
    void reserve_newKey_savesProcessingRecord() {
        User user = mock(User.class);
        Idempotency idempotency = processing(user, IdempotencyOperationType.TRANSFER, "new-key", "request-hash");
        when(idempotencyRepository.findByUser_IdAndOperationTypeAndIdempotencyKey(1L, IdempotencyOperationType.TRANSFER, "new-key"))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(idempotencyRepository.saveAndFlush(any(Idempotency.class))).thenReturn(idempotency);

        IdempotencyReserveResult<TransferRes> result = idempotencyService.reserve(
                1L,
                IdempotencyOperationType.TRANSFER,
                "new-key",
                "request-hash",
                TransferRes.class
        );

        assertFalse(result.replay());
        assertSame(idempotency, result.idempotency());
        assertNull(result.storedResponse());
        verify(idempotencyRepository).saveAndFlush(any(Idempotency.class));
    }

    @Test
    @DisplayName("동시 동일 키 선점 충돌은 현재 트랜잭션에서 복구하지 않고 예외를 전파한다")
    void reserve_uniqueViolation_propagatesDataIntegrityViolation() {
        when(idempotencyRepository.findByUser_IdAndOperationTypeAndIdempotencyKey(1L, IdempotencyOperationType.TRANSFER, "same-key"))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(mock(User.class));
        when(idempotencyRepository.saveAndFlush(any(Idempotency.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> idempotencyService.reserve(
                        1L,
                        IdempotencyOperationType.TRANSFER,
                        "same-key",
                        "request-hash",
                        TransferRes.class
                )
        );

        verify(idempotencyRepository).saveAndFlush(any(Idempotency.class));
        verify(idempotencyRepository)
                .findByUser_IdAndOperationTypeAndIdempotencyKey(1L, IdempotencyOperationType.TRANSFER, "same-key");
    }

    @Test
    @DisplayName("기존 SUCCESS 레코드는 저장된 응답을 반환한다")
    void reserve_existingSuccess_returnsStoredResponse() throws Exception {
        TransferRes storedResponse = new TransferRes(
                20L,
                TransferStatus.SUCCESS,
                10L,
                "100200300001",
                "100200300002",
                50_000L,
                50_000L,
                "점심값",
                LocalDateTime.of(2026, 6, 17, 10, 10)
        );
        Idempotency existing = success(mock(User.class), IdempotencyOperationType.TRANSFER, "same-key", "request-hash", storedResponse);
        when(idempotencyRepository.findByUser_IdAndOperationTypeAndIdempotencyKey(1L, IdempotencyOperationType.TRANSFER, "same-key"))
                .thenReturn(Optional.of(existing));

        IdempotencyReserveResult<TransferRes> result = idempotencyService.reserve(
                1L,
                IdempotencyOperationType.TRANSFER,
                "same-key",
                "request-hash",
                TransferRes.class
        );

        assertTrue(result.replay());
        assertNull(result.idempotency());
        assertEquals(storedResponse, result.storedResponse());
    }

    @Test
    @DisplayName("같은 키의 기존 레코드와 요청 해시가 다르면 충돌 예외를 발생시킨다")
    void reserve_existingDifferentRequest_throwsConflict() {
        Idempotency existing = processing(mock(User.class), IdempotencyOperationType.TRANSFER, "same-key", "existing-hash");
        when(idempotencyRepository.findByUser_IdAndOperationTypeAndIdempotencyKey(1L, IdempotencyOperationType.TRANSFER, "same-key"))
                .thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> idempotencyService.reserve(
                        1L,
                        IdempotencyOperationType.TRANSFER,
                        "same-key",
                        "new-hash",
                        TransferRes.class
                )
        );

        assertEquals(GlobalErrorCode.IDEMPOTENCY_REQUEST_CONFLICT, exception.getErrorCode());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("멱등성 키가 없으면 필수 예외를 발생시킨다")
    void reserve_blankKey_throwsRequired() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> idempotencyService.reserve(
                        1L,
                        IdempotencyOperationType.TRANSFER,
                        " ",
                        "request-hash",
                        TransferRes.class
                )
        );

        assertEquals(GlobalErrorCode.IDEMPOTENCY_KEY_REQUIRED, exception.getErrorCode());
        verify(idempotencyRepository, never()).findByUser_IdAndOperationTypeAndIdempotencyKey(any(), any(), any());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("멱등성 키가 100자를 초과하면 형식 예외를 발생시킨다")
    void reserve_tooLongKey_throwsInvalid() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> idempotencyService.reserve(
                        1L,
                        IdempotencyOperationType.TRANSFER,
                        "a".repeat(101),
                        "request-hash",
                        TransferRes.class
                )
        );

        assertEquals(GlobalErrorCode.IDEMPOTENCY_KEY_INVALID, exception.getErrorCode());
        verify(idempotencyRepository, never()).findByUser_IdAndOperationTypeAndIdempotencyKey(any(), any(), any());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("실패 완료 처리 시 멱등성 레코드를 FAILED 상태로 변경한다")
    void completeFailure_marksIdempotencyAsFailed() {
        Idempotency idempotency = processing(mock(User.class), IdempotencyOperationType.TRANSFER, "failed-key", "request-hash");
        when(idempotencyRepository.findById(1L)).thenReturn(Optional.of(idempotency));

        idempotencyService.completeFailure(1L);

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("오래된 PROCESSING 레코드를 EXPIRED 상태로 변경하고 만료 건수를 반환한다")
    void expireStaleProcessing_expiresProcessingRecordsAndReturnsCount() {
        Idempotency first = processing(mock(User.class), IdempotencyOperationType.TRANSFER, "first-key", "request-hash-1");
        Idempotency second = processing(mock(User.class), IdempotencyOperationType.DEPOSIT, "second-key", "request-hash-2");
        when(idempotencyRepository.findStaleProcessing(any()))
                .thenReturn(List.of(first, second));

        int expiredCount = idempotencyService.expireStaleProcessing(Duration.ofMinutes(10));

        assertEquals(2, expiredCount);
        assertEquals(IdempotencyStatus.EXPIRED, first.getStatus());
        assertEquals(IdempotencyStatus.EXPIRED, second.getStatus());
        assertNotNull(first.getCompletedAt());
        assertNotNull(second.getCompletedAt());
        verify(idempotencyRepository).findStaleProcessing(any());
    }

    private Idempotency processing(
            User user,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash
    ) {
        return Idempotency.processing(user, operationType, idempotencyKey, requestHash);
    }

    private Idempotency success(
            User user,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash,
            TransferRes response
    ) throws Exception {
        Idempotency idempotency = Idempotency.processing(user, operationType, idempotencyKey, requestHash);
        idempotency.complete(objectMapper.writeValueAsString(response));
        ReflectionTestUtils.setField(idempotency, "id", 1L);
        return idempotency;
    }
}
