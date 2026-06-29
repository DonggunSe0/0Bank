package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountTransactionSnapshot;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountAuthException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountCryptoException;
import com.team10.backend.domain.codef.exAccount.service.CodefExAccountGateway;
import com.team10.backend.domain.exAccount.dto.req.ExAccountTransactionSyncReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRefreshRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.entity.ExAccountConnection;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import com.team10.backend.domain.exAccount.exception.ExAccountConnectionErrorCode;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountConnectionRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import com.team10.backend.global.lock.DistributedLockTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

/**
 * 연동된 외부 계좌의 거래내역 조회 및 실시간 동기화/갱신 처리를 담당하는 서비스 클래스입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExAccountTransactionService {

    private final ExAccountTransactionRepository transactionRepository;
    private final ExAccountRepository accountRepository;
    private final ExAccountConnectionRepository connectionRepository;
    private final ExAccountService exAccountService;
    private final CodefExAccountGateway codefExAccountGateway;
    private final ExAccountSyncService exAccountSyncService;
    private final DistributedLockTemplate lockTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 특정 사용자가 연동한 모든 외부 계좌들의 통합 거래내역 목록을 최신순으로 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 최신순으로 정렬된 외부 거래내역 응답 DTO 리스트
     */
    public List<ExAccountTransactionRes> getTransactions(Long userId) {
        return transactionRepository.findAllByExAccountUserIdOrderByTransactedAtDesc(userId).stream()
                .map(ExAccountTransactionRes::from)
                .toList();
    }

    /**
     * 특정 계좌의 실시간 거래내역을 동기화하여 저장 또는 갱신하고, 해당 외부 계좌의 최종 거래 시각을 업데이트합니다.
     *
     * @param userId       사용자 ID
     * @param exAccountId  외부 계좌 ID
     * @param transactions 외부기관으로부터 신규 수신한 거래내역 동기화 요청 DTO 리스트
     * @return 추가/변경 통계 및 업데이트된 계좌 상세 결과를 담은 갱신 응답 DTO
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExAccountTransactionRefreshRes refreshTransactions(
            Long userId,
            Long exAccountId,
            List<ExAccountTransactionSyncReq> transactions
    ) {
        String lockKey = "lock:ex-account:transactions:" + exAccountId;
        return lockTemplate.executeWithLock(
                lockKey,
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                ExAccountErrorCode.EX_ACCOUNT_CONCURRENT_SYNC,
                () -> transactionTemplate.execute(status -> {
                    validateTransactions(transactions);

                    ExAccount account = accountRepository.findByIdAndUserId(exAccountId, userId)
                            .orElseThrow(() -> new BusinessException(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND));

                    int createdCount = 0;
                    int updatedCount = 0;

                    for (ExAccountTransactionSyncReq transaction : transactions) {
                        if (upsertTransaction(account, transaction)) {
                            createdCount++;
                            continue;
                        }

                        updatedCount++;
                    }

                    // 수신된 거래 내역들 중 가장 최신의 거래 일자로 계좌의 마지막 거래 시각 갱신
                    transactions.stream()
                            .map(ExAccountTransactionSyncReq::transactedAt)
                            .max(Comparator.naturalOrder())
                            .map(LocalDate::from)
                            .ifPresent(account::updateLastTransactionAt);

                    ExAccountDetailRes detail = exAccountService.getAccountDetail(userId, exAccountId);
                    return ExAccountTransactionRefreshRes.of(transactions.size(), createdCount, updatedCount, detail);
                })
        );
    }

    /**
     * 저장된 CODEF 연결정보로 외부기관 거래내역을 직접 조회한 뒤 기존 upsert 경로로 반영합니다.
     * 원계좌번호는 DB에 저장하지 않고, 보유계좌 재조회 결과를 HMAC 해시로 대조해 메모리 안에서만 사용합니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExAccountTransactionRefreshRes refreshTransactionsFromProvider(Long userId, Long exAccountId) {
        ExAccount account = accountRepository.findByIdAndUserId(exAccountId, userId)
                .orElseThrow(() -> new BusinessException(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND));
        ExAccountConnection connection = connectionRepository.findByUserIdAndOrganization(userId, account.getOrganization())
                .orElseThrow(() -> new BusinessException(ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_NOT_FOUND));
        if (!connection.isActive()) {
            throw new BusinessException(ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_INACTIVE);
        }

        String birthDate = account.getUser().getBirthDate().format(DateTimeFormatter.ofPattern("yyMMdd"));
        CodefExAccountSnapshot snapshot = findCurrentAccountSnapshot(account, connection, birthDate);
        List<CodefExAccountTransactionSnapshot> snapshots = getTransactionSnapshots(account, connection, birthDate, snapshot.accountNumber());
        List<ExAccountTransactionSyncReq> transactions = snapshots.stream()
                .map(this::toSyncRequest)
                .toList();

        if (transactions.isEmpty()) {
            return ExAccountTransactionRefreshRes.of(
                    0,
                    0,
                    0,
                    exAccountService.getAccountDetail(userId, exAccountId)
            );
        }

        return refreshTransactions(userId, exAccountId, transactions);
    }

    /**
     * 동기화 수신된 전체 거래내역 목록의 기본 무결성 검증을 수행합니다.
     */
    private void validateTransactions(List<ExAccountTransactionSyncReq> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        transactions.forEach(this::validateTransaction);
    }

    /**
     * 개별 거래내역 필수 파라미터(트랜잭션 고유 키, 일자, 방향, 금액 등)의 Null 여부 및 누락 여부를 검증합니다.
     */
    private void validateTransaction(ExAccountTransactionSyncReq transaction) {
        if (transaction == null
                || !hasText(transaction.transactionKey())
                || transaction.transactedAt() == null
                || transaction.direction() == null
                || transaction.amount() == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 단일 거래내역을 RDB에 반영합니다.
     * transactionKey 기준으로 중복 여부를 판별하여, 새로운 거래이면 추가하고 기존 거래이면 갱신(덮어쓰기)합니다.
     *
     * @return 신규 등록(Insert)된 경우 true, 기존 내용 업데이트(Update)인 경우 false 반환
     */
    private boolean upsertTransaction(ExAccount account, ExAccountTransactionSyncReq request) {
        boolean isNew = transactionRepository
                .findByExAccountIdAndTransactionKey(account.getId(), request.transactionKey())
                .isEmpty();

        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        transactionRepository.upsert(
                account.getId(),
                request.transactionKey(),
                request.transactedAt(),
                request.direction().name(),
                request.amount() == null ? java.math.BigDecimal.ZERO : request.amount(),
                request.balanceAfter(),
                request.counterpartyName(),
                request.memo(),
                request.rawCategory(),
                now,
                now
        );

        return isNew;
    }

    private CodefExAccountSnapshot findCurrentAccountSnapshot(
            ExAccount account,
            ExAccountConnection connection,
            String birthDate
    ) {
        List<CodefExAccountSnapshot> snapshots;
        try {
            snapshots = codefExAccountGateway.getAccountSnapshots(
                    account.getOrganization(),
                    connection.encryptedConnectedId(),
                    birthDate
            );
        } catch (CodefExAccountAuthException | CodefExAccountCryptoException exception) {
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_UNAVAILABLE,
                    exception
            );
        } catch (CodefExAccountClientException exception) {
            log.warn("[CODEF] 보유계좌 응답 처리 실패. userId={}, exAccountId={}, organization={}, reason={}",
                    account.getUser().getId(), account.getId(), account.getOrganization(), exception.getMessage());
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_INVALID_RESPONSE,
                    exception
            );
        }

        return snapshots.stream()
                .filter(snapshot -> account.getAccountNumberHash()
                        .equals(exAccountSyncService.getAccountNumberHash(snapshot.accountNumber())))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND));
    }

    private List<CodefExAccountTransactionSnapshot> getTransactionSnapshots(
            ExAccount account,
            ExAccountConnection connection,
            String birthDate,
            String accountNumber
    ) {
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = account.getLastTransactionAt() == null
                ? endDate.minusMonths(3)
                : account.getLastTransactionAt().minusDays(7);
        try {
            return codefExAccountGateway.getTransactionSnapshots(
                    account.getOrganization(),
                    connection.encryptedConnectedId(),
                    birthDate,
                    accountNumber,
                    startDate,
                    endDate
            );
        } catch (CodefExAccountAuthException | CodefExAccountCryptoException exception) {
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_UNAVAILABLE,
                    exception
            );
        } catch (CodefExAccountClientException exception) {
            log.warn("[CODEF] 거래내역 응답 처리 실패. userId={}, exAccountId={}, organization={}, reason={}",
                    account.getUser().getId(), account.getId(), account.getOrganization(), exception.getMessage());
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_INVALID_RESPONSE,
                    exception
            );
        }
    }

    private ExAccountTransactionSyncReq toSyncRequest(CodefExAccountTransactionSnapshot snapshot) {
        return new ExAccountTransactionSyncReq(
                snapshot.transactionKey(),
                snapshot.transactedAt(),
                snapshot.direction(),
                snapshot.amount(),
                snapshot.balanceAfter(),
                snapshot.counterpartyName(),
                snapshot.memo(),
                snapshot.rawCategory()
        );
    }
}
