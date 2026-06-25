package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 외부 연동 계좌 정보를 조회하는 비즈니스 로직을 처리하는 서비스 클래스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExAccountService {
    private final ExAccountRepository accountRepository;
    private final ExAccountTransactionRepository transactionRepository;

    /**
     * 특정 사용자가 연동 완료한 모든 외부 계좌 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 연동된 외부 계좌 목록 응답 DTO 리스트
     */
    public List<ExAccountRes> getAccounts(Long userId) {
        return accountRepository.findAllByUserId(userId).stream()
                .map(ExAccountRes::from)
                .toList();
    }

    /**
     * 특정 외부 계좌의 상세 정보와 최근 거래 내역들을 한꺼번에 조회합니다.
     *
     * @param userId      사용자 ID
     * @param exAccountId 외부 계좌 ID
     * @return 계좌 상세 및 거래 내역 정보를 포함한 응답 DTO
     */
    public ExAccountDetailRes getAccountDetail(Long userId, Long exAccountId) {
        ExAccount account = accountRepository.findByIdAndUserId(exAccountId, userId)
                .orElseThrow(() -> new BusinessException(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND));

        return getAccountDetail(account, userId);
    }

    /**
     * 단일 계좌 엔티티와 해당 계좌의 전체 거래 내역을 결합하여 상세 응답 DTO를 빌드합니다.
     */
    private ExAccountDetailRes getAccountDetail(ExAccount account, Long userId) {
        List<ExAccountTransactionRes> transactions = transactionRepository
                .findAllByExAccountIdAndExAccountUserIdOrderByTransactedAtDesc(account.getId(), userId)
                .stream()
                .map(ExAccountTransactionRes::from)
                .toList();

        return ExAccountDetailRes.of(ExAccountRes.from(account), transactions);
    }
}
