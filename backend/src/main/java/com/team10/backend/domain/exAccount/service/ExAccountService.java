package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExAccountService {
    private final ExAccountRepository accountRepository;

    public List<ExAccountRes> getAccounts(Long userId) {
        return accountRepository.findAllByUserId(userId).stream()
                .map(ExAccountRes::from)
                .toList();
    }

}
