package com.team10.backend.domain.transaction.service;

import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.transaction.dto.req.TransactionHistorySearchReq;
import com.team10.backend.domain.transaction.dto.res.TransactionHistoryDetailRes;
import com.team10.backend.domain.transaction.dto.res.TransactionHistorySearchRes;
import com.team10.backend.domain.transaction.exception.TransactionHistoryErrorCode;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionHistoryService {

    // MVP 기준으로 거래 내역 다건 조회 시 페이지 크기는 20 , 정렬 기준은 거래 일시 만으로 고정한다
    private static final int PAGE_SIZE = 20;
    public static final String SORT_PROPERTY_TRANSACTED_AT = "transactedAt";

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final AccountRepository accountRepository;

    public Page<TransactionHistorySearchRes> getTransactionHistories(
            Long accountId,
            Long userId,
            TransactionHistorySearchReq filter,
            int page,
            Sort.Direction sortDirection
    ) {
        // 요청 사용자가 해당 계정의 소유주인지 검증한다
        validateAccountOwner(accountId, userId);

        // 페이징 생성
        Pageable pageable = PageRequest.of(
                page,
                PAGE_SIZE,
                Sort.by(sortDirection, SORT_PROPERTY_TRANSACTED_AT)
        );

        return transactionHistoryRepository.search(accountId, filter, pageable);
    }

    public TransactionHistoryDetailRes getTransactionHistoryDetail(Long accountId, Long transactionId, Long userId) {
        // 요청 사용자가 해당 계정의 소유주인지 검증한다
        validateAccountOwner(accountId, userId);

        return transactionHistoryRepository.findByIdAndAccountId(transactionId, accountId)
                .map(TransactionHistoryDetailRes::from)
                .orElseThrow(() -> new BusinessException(TransactionHistoryErrorCode.TRANSACTION_HISTORY_NOT_FOUND));
    }

    private void validateAccountOwner(Long accountId, Long userId) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_ACCESS_DENIED));
    }
}
