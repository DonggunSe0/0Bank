package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.dto.req.ExAccountTransactionSyncReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRefreshRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

/**
 * 연동된 외부 계좌의 거래내역 조회 및 실시간 동기화/갱신 처리를 담당하는 서비스 클래스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExAccountTransactionService {

    private final ExAccountTransactionRepository transactionRepository;
    private final ExAccountRepository accountRepository;
    private final ExAccountService exAccountService;

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
    @Transactional
    public ExAccountTransactionRefreshRes refreshTransactions(
            Long userId,
            Long exAccountId,
            List<ExAccountTransactionSyncReq> transactions
    ) {
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
        ExAccountTransaction transaction = transactionRepository
                .findByExAccountIdAndTransactionKey(account.getId(), request.transactionKey())
                .orElse(null);

        if (transaction == null) {
            try {
                transactionRepository.saveAndFlush(request.toEntity(account));
            } catch (DataIntegrityViolationException exception) {
                ExAccountTransaction concurrentTransaction = transactionRepository
                        .findByExAccountIdAndTransactionKey(account.getId(), request.transactionKey())
                        .orElseThrow(() -> exception);
                request.applyTo(concurrentTransaction);
                return false;
            }
            return true;
        }

        request.applyTo(transaction);
        return false;
    }
}
