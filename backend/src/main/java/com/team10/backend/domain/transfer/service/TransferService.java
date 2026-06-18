package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.service.IdempotencyRequestHasher;
import com.team10.backend.global.idempotency.service.IdempotencyReserveResult;
import com.team10.backend.global.idempotency.service.IdempotencyService;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final IdempotencyService idempotencyService;
    private final IdempotencyRequestHasher idempotencyRequestHasher;
    private final TransferBusinessService transferBusinessService;

    private static final String IDEMPOTENCY_UNIQUE_CONSTRAINT = "uk_idempotency_user_operation_key";

    public TopUpRes topUp(Long userId, String idempotencyKey, Long accountId, Long amount, String memo) {
        IdempotencyReserveResult<TopUpRes> reserveResult = reserveWithSingleRetry(
                userId,
                IdempotencyOperationType.DEPOSIT,
                idempotencyKey,
                idempotencyRequestHasher.generate(accountId, amount, memo),
                TopUpRes.class
        );

        if (reserveResult.replay()) {
            return reserveResult.storedResponse();
        }

        Idempotency idempotency = reserveResult.idempotency();

        try {

            TopUpRes response = transferBusinessService.executeTopUp(userId, accountId, amount, memo);

            idempotencyService.completeSuccess(idempotency.getId(), response);

            return response;
        } catch (BusinessException e) {
            idempotencyService.completeFailure(idempotency.getId());
            throw e;
        }
    }

    // 오케스트레이터 패턴 -> @Transactional 제거
    public TransferRes transfer(Long userId, String idempotencyKey, Long senderAccountId, String receiverAccountNumber, Long amount, String memo) {
        // 송금 시작 전에 멱등성 검증/선점
        IdempotencyReserveResult<TransferRes> reserveResult = reserveWithSingleRetry(
                userId,
                IdempotencyOperationType.TRANSFER,
                idempotencyKey,
                idempotencyRequestHasher.generate(senderAccountId, receiverAccountNumber, amount, memo),
                TransferRes.class);

        // 만약 이전에 들어왔던 요청이면 저장된 응답객체 반환하고 종료
        if (reserveResult.replay()) {
            return reserveResult.storedResponse();
        }

        Long idempotencyId = reserveResult.idempotency().getId();

        try{

            TransferRes response = transferBusinessService.executeTransfer(userId,
                    senderAccountId,
                    receiverAccountNumber,
                    amount,
                    memo);

            // Idempotency 객체의 상태를 SUCCESS로 변경 및 JSON 송금 성공 당시의 응답객체 저장
            idempotencyService.completeSuccess(idempotencyId, response);
            return response;
        } catch (BusinessException e) { // 비즈니스 예외가 발생했을 때에만(멱등 검증은 통과한 경우) 송금 멱등성 실패상태 기록
            idempotencyService.completeFailure(idempotencyId);
            throw e;
        }

    }

    // 멱등성 키에 대한 UK 예외를 받아서 reserve() 호출 실패 시 reserve()만 1회 재시도
    private <T> IdempotencyReserveResult<T> reserveWithSingleRetry(
            Long userId,
            IdempotencyOperationType operationType,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType
    ) {
        try {
            return idempotencyService.reserve(userId, operationType, idempotencyKey, requestHash, responseType);
        } catch (DataIntegrityViolationException e) {
            if (!isIdempotencyUniqueConstraintViolation(e)) {
                throw e; // 멱등성 유니크 예외아니면 그냥 예외 던지기
            }
            // 정확히 멱등성 유니크 예외인 경우만 1회 재조회
            return idempotencyService.reserve(userId, operationType, idempotencyKey, requestHash, responseType);
        }

    }

    // IDEMPOTENCY_UNIQUE_CONSTRAINT 예외인 경우만 true 반환
    private boolean isIdempotencyUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;

        while (current != null) {
            // Hibernate의 DB 제약 위반 예외인지 검증
            if (current instanceof ConstraintViolationException constraintViolationException) {
                // Hibernate 예외에서 실제 DB constraint 이름 꺼냄
                String constraintName = constraintViolationException.getConstraintName();

                // equalsIgnoreCase사용: DB나 Hibernate가 constraint 이름을 대문자로 반환할 수 있기 때문
                return constraintName != null
                        && constraintName.equalsIgnoreCase(IDEMPOTENCY_UNIQUE_CONSTRAINT);
            }

            // 또 다른 Throwable(예외) 객체 = 하위객체 | null
            current = current.getCause();
        }
        return false;
    }

}
