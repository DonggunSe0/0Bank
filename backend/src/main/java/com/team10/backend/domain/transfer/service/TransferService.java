package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.repository.AccountTransferVerification;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransferBusinessService transferBusinessService;

    // 오케스트레이터 패턴 -> @Transactional 제거
    public TopUpRes topUp(Long userId, Long accountId, Long amount, String memo) {
        return transferBusinessService.executeTopUp(userId, accountId, amount, memo);
    }

    // 오케스트레이터 패턴 -> @Transactional 제거
    public TransferRes transfer(
            Long userId,
            Long senderAccountId,
            String receiverAccountNumber,
            String accountPassword,
            Long amount,
            String memo
    ) {
        // amount 1원 이상 확인
        if (amount == null || amount < 1L) throw new BusinessException(TransferErrorCode.INVALID_INPUT_VALUE);

        AccountTransferVerification senderAccount = accountRepository.findTransferVerificationByIdAndUserId(
                        senderAccountId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_NOT_FOUND));

        if (senderAccount.status() != AccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (senderAccount.accountType() != AccountType.DEPOSIT) {
            throw new BusinessException(TransferErrorCode.INVALID_ACCOUNT_TYPE);
        }

        // 출금 계좌 비밀번호 일치 여부 확인
        validateAccountPassword(senderAccount.accountPasswordHash(), accountPassword);

        return transferBusinessService.executeTransfer(userId,
                senderAccountId,
                receiverAccountNumber,
                amount,
                memo);
    }

    private void validateAccountPassword(String accountPasswordHash, String accountPassword) {
        if (accountPasswordHash == null || accountPasswordHash.isBlank()) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_PASSWORD_NOT_SET);
        }

        if (accountPassword == null
                || accountPassword.isBlank()
                || !passwordEncoder.matches(accountPassword, accountPasswordHash)) {
            throw new BusinessException(TransferErrorCode.ACCOUNT_PASSWORD_MISMATCH);
        }
    }

}
