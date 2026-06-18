package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExAccountTransactionService {

    private final ExAccountTransactionRepository transactionRepository;

    public List<ExAccountTransactionRes> getTransactions(Long userId) {
        return transactionRepository.findAllByExAccountUserIdOrderByTransactedAtDesc(userId).stream()
                .map(ExAccountTransactionRes::from)
                .toList();
    }
}
