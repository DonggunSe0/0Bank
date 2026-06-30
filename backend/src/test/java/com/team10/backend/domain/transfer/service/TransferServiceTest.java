package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.repository.AccountTransferVerification;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TransferBusinessService transferBusinessService;

    @InjectMocks
    private TransferService transferService;

    @Test
    @DisplayName("입금 요청은 비즈니스 입금 서비스에 위임한다")
    void topUp_delegatesToBusinessService() {
        TopUpRes response = topUpResponse();
        when(transferBusinessService.executeTopUp(1L, 1L, 5_000L, "입금 메모"))
                .thenReturn(response);

        TopUpRes result = transferService.topUp(1L, 1L, 5_000L, "입금 메모");

        assertSame(response, result);
        verify(transferBusinessService).executeTopUp(1L, 1L, 5_000L, "입금 메모");
    }

    @Test
    @DisplayName("송금 요청은 출금 계좌 비밀번호를 검증한 뒤 비즈니스 송금 서비스에 위임한다")
    void transfer_delegatesToBusinessService() {
        TransferRes response = transferResponse();
        when(accountRepository.findTransferVerificationByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(accountVerification("encoded-password")));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(transferBusinessService.executeTransfer(1L, 1L, "100200300002", 50_000L, "점심값"))
                .thenReturn(response);

        TransferRes result = transferService.transfer(1L, 1L, "100200300002", "123456", 50_000L, "점심값");

        assertSame(response, result);
        verify(transferBusinessService).executeTransfer(1L, 1L, "100200300002", 50_000L, "점심값");
    }

    @Test
    @DisplayName("출금 계좌 비밀번호가 일치하지 않으면 비즈니스 송금 서비스를 호출하지 않는다")
    void transfer_passwordMismatch_throwsAccountPasswordMismatch() {
        when(accountRepository.findTransferVerificationByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(accountVerification("encoded-password")));
        when(passwordEncoder.matches("000000", "encoded-password")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.transfer(1L, 1L, "100200300002", "000000", 50_000L, "비밀번호 불일치")
        );

        assertEquals(TransferErrorCode.ACCOUNT_PASSWORD_MISMATCH, exception.getErrorCode());
        verify(transferBusinessService, never()).executeTransfer(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("출금 계좌 비밀번호가 설정되지 않으면 비즈니스 송금 서비스를 호출하지 않는다")
    void transfer_passwordNotSet_throwsAccountPasswordNotSet() {
        when(accountRepository.findTransferVerificationByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(accountVerification(null)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.transfer(1L, 1L, "100200300002", "123456", 50_000L, "비밀번호 미설정")
        );

        assertEquals(TransferErrorCode.ACCOUNT_PASSWORD_NOT_SET, exception.getErrorCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(transferBusinessService, never()).executeTransfer(any(), any(), any(), any(), any());
    }

    private TopUpRes topUpResponse() {
        return new TopUpRes(
                100L,
                1L,
                TransactionType.DEPOSIT,
                5_000L,
                10_000L,
                15_000L,
                "입금 메모",
                LocalDateTime.now()
        );
    }

    private TransferRes transferResponse() {
        return new TransferRes(
                20L,
                TransferStatus.SUCCESS,
                1L,
                "100200300001",
                "100200300002",
                50_000L,
                50_000L,
                "점심값",
                LocalDateTime.now()
        );
    }

    private AccountTransferVerification accountVerification(String accountPasswordHash) {
        return new AccountTransferVerification(
                1L,
                AccountType.DEPOSIT,
                AccountStatus.ACTIVE,
                accountPasswordHash
        );
    }
}
