package com.team10.backend.domain.transaction.repository;

import com.team10.backend.domain.transaction.dto.req.TransactionHistorySearchReq;
import com.team10.backend.domain.transaction.dto.res.TransactionHistorySearchRes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionHistoryRepositoryCustom {

    Page<TransactionHistorySearchRes> search(Long accountId, TransactionHistorySearchReq filter, Pageable pageable);
}
