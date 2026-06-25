package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationFailure;
import com.team10.backend.domain.codef.exAccount.service.CodefExAccountGateway;
import com.team10.backend.domain.codef.exAccount.store.CodefExAccountCandidateStore;
import com.team10.backend.domain.exAccount.type.ExAccountConnectionStatus;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import com.team10.backend.domain.exAccount.dto.res.ExAccountCandidateListRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountCandidateRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountConnectionRes;
import com.team10.backend.domain.exAccount.entity.ExAccountConnection;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import com.team10.backend.domain.exAccount.exception.ExAccountConnectionErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountConnectionRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.lock.DistributedLockTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExAccountConnectionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExAccountConnectionRepository connectionRepository;
    @Mock
    private CodefExAccountGateway codefExAccountGateway;
    @Mock
    private ExAccountSyncService exAccountSyncService;
    @Mock
    private ExAccountRepository exAccountRepository;
    @Mock
    private CodefExAccountCandidateStore candidateStore;
    @Mock
    private ExAccountCodefRateLimitService rateLimitService;
    @Mock
    private DistributedLockTemplate lockTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ExAccountConnectionService service;
    private User user;
    private CodefExAccountConnectionCreateReq createRequest;

    @BeforeEach
    void setUp() {
        service = new ExAccountConnectionService(
                userRepository,
                connectionRepository,
                codefExAccountGateway,
                exAccountSyncService,
                exAccountRepository,
                candidateStore,
                rateLimitService,
                lockTemplate,
                transactionTemplate
        );
        user = User.create(
                "test@example.com",
                "password123",
                "홍길동",
                "010-1234-5678",
                LocalDate.of(1999, 1, 1)
        );
        createRequest = new CodefExAccountConnectionCreateReq(
                "0004", "BK", "P", "1", "user123", "pass123", "990101"
        );

        // Default mock behaviors for lockTemplate and transactionTemplate to run synchronously
        org.mockito.Mockito.lenient().when(lockTemplate.executeWithLock(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
                });
    }

    @Test
    @DisplayName("연결 등록은 사용자+기관 단위 CODEF 계정등록 호출 지점이다")
    void registersConnectionProperly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        EncryptedConnectedId encryptedConnectedId = new EncryptedConnectedId(
                "ciphertext", "iv", "v1"
        );
        when(codefExAccountGateway.register(createRequest)).thenReturn(encryptedConnectedId);
        when(connectionRepository.findByUserIdAndOrganization(1L, "0004"))
                .thenReturn(Optional.empty());

        ExAccountConnection mockSaved = connection("ciphertext");
        when(connectionRepository.saveAndFlush(any())).thenReturn(mockSaved);

        ExAccountConnectionRes response = service.register(1L, createRequest);

        assertThat(response.organization()).isEqualTo("0004");
        assertThat(response.status()).isEqualTo(ExAccountConnectionStatus.ACTIVE);

        ArgumentCaptor<ExAccountConnection> captor =
                ArgumentCaptor.forClass(ExAccountConnection.class);
        verify(connectionRepository).saveAndFlush(captor.capture());
        ExAccountConnection captured = captor.getValue();
        assertThat(captured.getUser()).isEqualTo(user);
        assertThat(captured.getOrganization()).isEqualTo("0004");
        assertThat(captured.encryptedConnectedId()).isEqualTo(encryptedConnectedId);
        verify(rateLimitService).checkRegister(1L, "0004");
        verify(codefExAccountGateway).register(createRequest);
    }

    @Test
    void registerThrowsExceptionWhenLockAcquisitionFails() {
        org.mockito.Mockito.doThrow(new BusinessException(
                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_CONCURRENT_REQUEST
        )).when(lockTemplate).executeWithLock(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.register(1L, createRequest))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_CONCURRENT_REQUEST
                        ));
    }

    @Test
    void registerDoesNotCallCodefWhenUserOrganizationRateLimitIsExceeded() {
        doThrow(new BusinessException(
                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_REGISTER_RATE_LIMIT_EXCEEDED
        )).when(rateLimitService).checkRegister(1L, "0004");

        assertThatThrownBy(() -> service.register(1L, createRequest))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_REGISTER_RATE_LIMIT_EXCEEDED));

        verify(codefExAccountGateway, never()).register(any());
    }

    @Test
    void registerMapsProviderCredentialFailureToBusinessError() {
        when(codefExAccountGateway.register(createRequest))
                .thenThrow(new CodefExAccountRegistrationException(
                        CodefExAccountRegistrationFailure.CREDENTIAL_INVALID,
                        "invalid credential"
                ));

        assertThatThrownBy(() -> service.register(1L, createRequest))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_CREDENTIAL_INVALID));

        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("후보 조회는 사용자+기관 단위 CODEF 보유계좌 조회 호출 지점이다")
    void getsProviderAccountsAndReturnsMaskedCandidates() {
        ExAccountConnection connection = connection("ciphertext");
        CodefExAccountSnapshot snapshot = snapshot();
        ExAccountCandidateRes candidate = candidate();
        
        when(connectionRepository.findByUserIdAndOrganization(1L, "0004"))
                .thenReturn(Optional.of(connection));
        when(codefExAccountGateway.getAccountSnapshots(
                "0004", connection.encryptedConnectedId(), "990101"
        )).thenReturn(List.of(snapshot));
        
        when(candidateStore.save(1L, List.of(snapshot))).thenReturn("mock-token");
        when(exAccountSyncService.getAccountNumberHash("1234567890")).thenReturn("hash");
        when(exAccountSyncService.getMaskedAccountNumber("1234567890")).thenReturn("123***7890");
        when(exAccountRepository.findByUserIdAndOrganizationAndAccountNumberHash(1L, "0004", "hash"))
                .thenReturn(Optional.empty());

        ExAccountCandidateListRes result = service.getLinkCandidates(1L, "0004");

        assertThat(result.candidateToken()).isEqualTo("mock-token");
        assertThat(result.accounts()).containsExactly(candidate);
        assertThat(result.accounts().getFirst().accountNoMasked()).doesNotContain("1234567890");
        assertThat(connection.getLastSyncedAt()).isNotNull();
        verify(rateLimitService).checkAccountList(1L, "0004");
        verify(codefExAccountGateway).getAccountSnapshots(
                "0004", connection.encryptedConnectedId(), "990101");
    }

    @Test
    void getLinkCandidatesDoesNotCallCodefWhenUserOrganizationRateLimitIsExceeded() {
        ExAccountConnection connection = connection("ciphertext");
        when(connectionRepository.findByUserIdAndOrganization(1L, "0004"))
                .thenReturn(Optional.of(connection));
        doThrow(new BusinessException(
                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_ACCOUNT_LIST_RATE_LIMIT_EXCEEDED
        )).when(rateLimitService).checkAccountList(1L, "0004");

        assertThatThrownBy(() -> service.getLinkCandidates(1L, "0004"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_ACCOUNT_LIST_RATE_LIMIT_EXCEEDED));

        verify(codefExAccountGateway, never()).getAccountSnapshots(any(), any(), any());
    }

    @Test
    void getLinkCandidatesMapsProviderAccountListFailureToBusinessError() {
        ExAccountConnection connection = connection("ciphertext");
        when(connectionRepository.findByUserIdAndOrganization(1L, "0004"))
                .thenReturn(Optional.of(connection));
        when(codefExAccountGateway.getAccountSnapshots(
                "0004", connection.encryptedConnectedId(), "990101"
        )).thenThrow(new CodefExAccountClientException("bad provider response"));

        assertThatThrownBy(() -> service.getLinkCandidates(1L, "0004"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_INVALID_RESPONSE));
    }

    @Test
    void returnsEmptyCandidatesWhenProviderHasNoAccounts() {
        ExAccountConnection connection = connection("ciphertext");
        when(connectionRepository.findByUserIdAndOrganization(1L, "0004"))
                .thenReturn(Optional.of(connection));
        when(codefExAccountGateway.getAccountSnapshots(
                "0004", connection.encryptedConnectedId(), "990101"
        )).thenReturn(List.of());

        ExAccountCandidateListRes result = service.getLinkCandidates(1L, "0004");
        assertThat(result.candidateToken()).isEmpty();
        assertThat(result.accounts()).isEmpty();
        assertThat(connection.getLastSyncedAt()).isNotNull();
    }

    @Test
    void blocksAnotherUsersConnectionLookup() {
        when(connectionRepository.findByUserIdAndOrganization(2L, "0004"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLinkCandidates(2L, "0004"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_NOT_FOUND));
        verify(codefExAccountGateway, never()).getAccountSnapshots(any(), any(), any());
    }

    @Test
    void blocksInactiveConnectionBeforeCallingProvider() {
        ExAccountConnection connection = connection("ciphertext");
        connection.requireReauthentication();
        when(connectionRepository.findByUserIdAndOrganization(1L, "0004"))
                .thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.getLinkCandidates(1L, "0004"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_INACTIVE));
        verify(codefExAccountGateway, never()).getAccountSnapshots(any(), any(), any());
    }

    private ExAccountConnection connection(String ciphertext) {
        return ExAccountConnection.create(
                user,
                "0004",
                new EncryptedConnectedId(ciphertext, "iv", "v1")
        );
    }

    private CodefExAccountSnapshot snapshot() {
        return new CodefExAccountSnapshot(
                "0004", "1234567890", "입출금통장", "생활비",
                ExAccountType.DEMAND, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                LocalDate.of(2024, 1, 1), null, LocalDate.of(2026, 6, 22)
        );
    }

    private ExAccountCandidateRes candidate() {
        return new ExAccountCandidateRes(
                0, "0004", "123***7890", "입출금통장", "생활비",
                ExAccountType.DEMAND, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                LocalDate.of(2024, 1, 1), null, LocalDate.of(2026, 6, 22), false
        );
    }
}
