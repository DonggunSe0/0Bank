package com.team10.backend.domain.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transfer.dto.res.DepositRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.entity.TransferIdempotency;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.transfer.event.TransferFailedEvent;
import com.team10.backend.domain.transfer.repository.TransferIdempotencyRepository;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.transfer.type.IdempotencyStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransferIdempotencyRepository transferIdempotencyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // todo: 개선 및 고도화 진행
    @Transactional
    public DepositRes topUp(Long userId, Long accountId, Long amount, String memo) {

        // amount 1원 이상 확인
        if(amount == null || amount < 1L) throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);

        // accountId로 계좌 조회 & 로그인 사용자 소유 계좌인지 확인 -> 비관적락
        Account account = accountRepository.findByIdAndUserIdForUpdate(accountId, userId).orElseThrow(
                () -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND)
        );

        // 계좌 상태 ACTIVE 확인
        if (!account.isActive()) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 입금 전 잔액 캡쳐
        Long balanceBefore = account.getBalance();
        // 입금
        account.deposit(amount);

        // TransactionHistory(DEPOSIT, IN) 저장
        LocalDateTime transactedAt = LocalDateTime.now();

        TransactionHistory transactionHistory = TransactionHistory.createDeposit(
                account,
                amount,
                balanceBefore,
                account.getBalance(),
                memo,
                transactedAt
        );

        TransactionHistory savedHistory = transactionHistoryRepository.save(transactionHistory);

        return DepositRes.from(savedHistory);
    }

    @Transactional
    public TransferRes transfer(Long userId, String idempotencyKey, Long senderAccountId, String receiverAccountNumber, Long amount, String memo) {
        // 송금 시작 전에 멱등성 검증/선점

        // 멱등성 키 존재 검증
        validateIdempotencyKey(idempotencyKey);

        // 요청 바디값으로 해시값 생성 (동일 멱등성 키여도 다른 작업이면 해시값 달라짐)
        String requestHash = generateRequestHash(senderAccountId, receiverAccountNumber, amount, memo);

        // 존재하는 객체
        Optional<TransferIdempotency> existing =
                transferIdempotencyRepository.findByUser_IdAndIdempotencyKey(userId, idempotencyKey);

        if (existing.isPresent()) {
            TransferIdempotency idempotency = existing.get();

            if (!idempotency.getRequestHash().equals(requestHash)) {
                throw new BusinessException(TransferErrorCode.IDEMPOTENCY_REQUEST_CONFLICT);
            }

            if (idempotency.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new BusinessException(TransferErrorCode.IDEMPOTENCY_REQUEST_PROCESSING);
            }

            // 같은 key 재요청 -> idempotency에 저장된 responseBody 읽어서 TransferRes변환 후 return
            if (idempotency.getStatus() == IdempotencyStatus.SUCCESS) {
                return deserializeTransferResponse(idempotency.getResponseBody());
            }

        }

        // amount 1원 이상 확인
        if(amount == null || amount < 1L) throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);

        // 락 획득 전에 멱등성 키 처리
        User user = userRepository.getReferenceById(userId); // 이 ID를 가진 User를 참조하는 프록시 객체를 생성
        TransferIdempotency idempotency = transferIdempotencyRepository.save(
                TransferIdempotency.processing(user, idempotencyKey, requestHash)
        );

        // 락 획득
        LockedTransferAccounts accounts =
                lockTransferAccounts(senderAccountId, receiverAccountNumber);

        Account senderAccount = accounts.sender();
        Account receiverAccount = accounts.receiver();

        // 출금 계좌가 로그인 유저의 소유인지 확인
        validateSenderOwner(senderAccount, userId);
        // 서로 다른 계좌인지 확인
        validateDifferentAccounts(senderAccount, receiverAccount);
        // 두 계좌 모두 ACTIVE인지 확인
        validateAccountsActive(senderAccount, receiverAccount);

        // 이전 잔액 캡쳐
        Long senderBalanceBefore = senderAccount.getBalance();
        Long receiverBalanceBefore = receiverAccount.getBalance();

        // 출금 계좌 잔액 충분한지 확인 -> account.withdraw() 내부에 확인 로직 구현
        try {
            senderAccount.withdraw(amount);
        } catch (BusinessException e) {
            if (e.getErrorCode() == TransferErrorCode.INSUFFICIENT_BALANCE) {
                publishTransferFailedEvent(senderAccount.getId(), receiverAccount.getId(), amount, memo);
            }
            throw e;
        }
        receiverAccount.deposit(amount); // 수취 계좌 balance 증가

        // Transfer(SUCCESS) 저장
        Transfer transfer = transferRepository.save(Transfer.success(senderAccount, receiverAccount, amount, memo));
        LocalDateTime transferredAt = LocalDateTime.now();
        // 출금 계좌 TransactionHistory(TRANSFER, OUT) 저장
        TransactionHistory senderHistory = TransactionHistory.createTransferOut(
                senderAccount,
                transfer,
                amount,
                senderBalanceBefore,
                senderAccount.getBalance(),
                receiverAccount.getAccountNumber(),
                receiverAccount.getUser().getName(),
                memo,
                transferredAt
        );

        // 수취 계좌 TransactionHistory(TRANSFER, IN) 저장
        TransactionHistory receiverHistory = TransactionHistory.createTransferIn(
                receiverAccount,
                transfer,
                amount,
                receiverBalanceBefore,
                receiverAccount.getBalance(),
                senderAccount.getAccountNumber(),
                senderAccount.getUser().getName(),
                memo,
                transferredAt
        );

        transactionHistoryRepository.save(senderHistory);
        transactionHistoryRepository.save(receiverHistory);

        TransferRes response = TransferRes.from(transfer, transferredAt);
        idempotency.complete(transfer, serializeTransferResponse(response)); // 응답 JSON 형태로 저장

        return response;
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(TransferErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    private LockedTransferAccounts lockTransferAccounts(
            Long senderAccountId,
            String receiverAccountNumber
    ){
        // receiverAccountNumber로 수취 계좌 ID 조회
        Long receiverAccountId = accountRepository.findIdByAccountNumber(receiverAccountNumber)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND));

        validateDifferentAccountIds(senderAccountId, receiverAccountId);

        Long firstId = Math.min(senderAccountId, receiverAccountId);
        Long secondId = Math.max(senderAccountId, receiverAccountId);

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND));

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND));

        Account senderAccount = first.getId().equals(senderAccountId) ? first : second;
        Account receiverAccount = first.getId().equals(receiverAccountId) ? first : second;

        return new LockedTransferAccounts(senderAccount, receiverAccount);
    }

    private record LockedTransferAccounts(
            Account sender,
            Account receiver
    ) {
    }

    private void validateDifferentAccounts(Account senderAccount, Account receiverAccount) {
        if (senderAccount.getId().equals(receiverAccount.getId())) {
            throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateDifferentAccountIds(Long senderAccountId, Long receiverAccountId) {
        if (senderAccountId.equals(receiverAccountId)) {
            throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateAccountsActive(Account senderAccount, Account receiverAccount) {
        if (!senderAccount.isActive() || !receiverAccount.isActive()) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    private void validateSenderOwner(Account senderAccount, Long loginUserId) {
        if (!senderAccount.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void publishTransferFailedEvent(
            Long senderAccountId,
            Long receiverAccountId,
            Long amount,
            String memo
    ) {
        eventPublisher.publishEvent(new TransferFailedEvent(senderAccountId, receiverAccountId, amount, memo));
    }

/*
    요청
    {
        "senderAccountId": 1,
            "receiverAccountNumber": "100200300002",
            "amount": 50000,
            "memo": "점심값"
    }
    -> 내부 문자열: 1|100200300002|50000|점심값
    -> SHA-256 해시로 바꿔 저장
*/
    private String generateRequestHash(Long senderAccountId, String receiverAccountNumber, Long amount, String memo) {
        String normalizedMemo = memo == null ? "" : memo.trim();

        String raw = String.join("|",
                String.valueOf(senderAccountId),
                receiverAccountNumber,
                String.valueOf(amount),
                normalizedMemo
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }

    }

    // 직렬화 (자바객체 -> JSON)
    private String serializeTransferResponse(TransferRes response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize transfer response", e);
        }
    }

    // 역직렬화 (JSON -> 자바객체)
    private TransferRes deserializeTransferResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, TransferRes.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize transfer response", e);
        }
    }
}
