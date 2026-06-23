package com.team10.backend.domain.exAccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.store.CodefExAccountCandidateStore;
import com.team10.backend.domain.exAccount.type.ExAccountStatus;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import com.team10.backend.domain.exAccount.dto.req.ExAccountLinkReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import com.team10.backend.global.security.HmacSha256Hasher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExAccountSyncServiceTest {

    private static final String ACCOUNT_NUMBER_HASH = "a".repeat(64);

    @Mock
    private ExAccountRepository exAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HmacSha256Hasher hmacSha256Hasher;

    @Mock
    private CodefExAccountCandidateStore candidateStore;

    @InjectMocks
    private ExAccountSyncService exAccountSyncService;

    private User user;

    @BeforeEach
    void setUp() {
        user = createUser(1L);
    }

    @Test
    @DisplayName("토큰이 비어있으면 공통 입력 오류로 실패한다")
    void linkAccountsWithEmptyToken() {
        ExAccountLinkReq request = new ExAccountLinkReq("", List.of(0));

        assertThatThrownBy(() -> exAccountSyncService.linkAccounts(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(exAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Redis에 저장된 후보 정보가 없으면 만료 오류로 실패한다")
    void linkAccountsWithExpiredToken() {
        ExAccountLinkReq request = new ExAccountLinkReq("invalid-token", List.of(0));
        claimToken(1L, "invalid-token");
        when(candidateStore.get(1L, "invalid-token")).thenReturn(List.of());

        assertThatThrownBy(() -> exAccountSyncService.linkAccounts(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExAccountErrorCode.EX_ACCOUNT_CANDIDATE_NOT_FOUND);

        verify(exAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("선택한 인덱스가 범위를 벗어나면 유효하지 않은 인덱스 오류로 실패한다")
    void linkAccountsWithInvalidIndex() {
        ExAccountLinkReq request = new ExAccountLinkReq("token", List.of(99));
        CodefExAccountSnapshot snapshot = snapshot();
        claimToken(1L, "token");
        when(candidateStore.get(1L, "token")).thenReturn(List.of(snapshot));

        assertThatThrownBy(() -> exAccountSyncService.linkAccounts(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExAccountErrorCode.EX_ACCOUNT_CANDIDATE_INVALID_INDEX);

        verify(exAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("연동 버튼을 누른 외부 계좌가 없으면 신규 저장한다")
    void linkAccountCreate() {
        ExAccountLinkReq request = new ExAccountLinkReq("token", List.of(0));
        CodefExAccountSnapshot snapshot = snapshot();

        claimToken(1L, "token");
        when(candidateStore.get(1L, "token")).thenReturn(List.of(snapshot));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(hmacSha256Hasher.hash("1234567890")).thenReturn(ACCOUNT_NUMBER_HASH);
        when(exAccountRepository.findByUserIdAndOrganizationAndAccountNumberHash(
                1L,
                "0004",
                ACCOUNT_NUMBER_HASH
        )).thenReturn(Optional.empty());
        when(exAccountRepository.save(any(ExAccount.class))).thenAnswer(invocation -> {
            ExAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 10L);
            return account;
        });

        List<ExAccountRes> responses = exAccountSyncService.linkAccounts(1L, request);

        assertThat(responses).hasSize(1);
        ExAccountRes response = responses.get(0);
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.organization()).isEqualTo("0004");
        assertThat(response.accountNoMasked()).isEqualTo("123***7890");
        assertThat(response.accountName()).isEqualTo("입출금통장");
        assertThat(response.status()).isEqualTo(ExAccountStatus.ACTIVE);

        ArgumentCaptor<ExAccount> accountCaptor = ArgumentCaptor.forClass(ExAccount.class);
        verify(exAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAccountNumberHash()).isEqualTo(ACCOUNT_NUMBER_HASH);
        assertThat(accountCaptor.getValue().getAccountNumberMasked()).isEqualTo("123***7890");
        verify(candidateStore).remove(1L, "token");
        verify(candidateStore).releaseClaim(1L, "token", "claim-id");
    }

    @Test
    @DisplayName("이미 연동된 외부 계좌는 새로 저장하지 않고 스냅샷을 갱신한다")
    void linkAccountUpdate() {
        ExAccountLinkReq request = new ExAccountLinkReq("token", List.of(0));
        CodefExAccountSnapshot snapshot = snapshot();
        ExAccount existingAccount = createExAccount(10L, user, snapshot);

        claimToken(1L, "token");
        when(candidateStore.get(1L, "token")).thenReturn(List.of(snapshot));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(hmacSha256Hasher.hash("1234567890")).thenReturn(ACCOUNT_NUMBER_HASH);
        when(exAccountRepository.findByUserIdAndOrganizationAndAccountNumberHash(
                1L,
                "0004",
                ACCOUNT_NUMBER_HASH
        )).thenReturn(Optional.of(existingAccount));

        List<ExAccountRes> responses = exAccountSyncService.linkAccounts(1L, request);

        assertThat(responses).hasSize(1);
        ExAccountRes response = responses.get(0);
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.accountName()).isEqualTo("입출금통장");
        verify(exAccountRepository, never()).save(any());
        verify(candidateStore).remove(1L, "token");
        verify(candidateStore).releaseClaim(1L, "token", "claim-id");
    }

    @Test
    @DisplayName("이미 claim된 후보 토큰은 후보 목록을 읽기 전에 거절한다")
    void linkAccountsRejectsAlreadyClaimedCandidateTokenBeforeReadingCandidates() {
        ExAccountLinkReq request = new ExAccountLinkReq("token", List.of(0));
        when(candidateStore.claim(1L, "token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exAccountSyncService.linkAccounts(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExAccountErrorCode.EX_ACCOUNT_CANDIDATE_ALREADY_CLAIMED);

        verify(candidateStore, never()).get(1L, "token");
        verify(candidateStore, never()).remove(1L, "token");
        verify(candidateStore, never()).releaseClaim(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 외부 계좌를 연동할 수 없다")
    void linkAccountWithNotFoundUser() {
        ExAccountLinkReq request = new ExAccountLinkReq("token", List.of(0));
        CodefExAccountSnapshot snapshot = snapshot();

        when(candidateStore.claim(999L, "token")).thenReturn(Optional.of("claim-id"));
        when(candidateStore.get(999L, "token")).thenReturn(List.of(snapshot));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exAccountSyncService.linkAccounts(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(exAccountRepository, never()).save(any());
        verify(candidateStore).releaseClaim(999L, "token", "claim-id");
    }

    private void claimToken(Long userId, String token) {
        when(candidateStore.claim(userId, token)).thenReturn(Optional.of("claim-id"));
    }

    private CodefExAccountSnapshot snapshot() {
        return new CodefExAccountSnapshot(
                "0004", "1234567890", "입출금통장", "생활비",
                ExAccountType.DEMAND, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                LocalDate.of(2024, 1, 1), null, LocalDate.of(2026, 6, 22)
        );
    }

    private ExAccount createExAccount(Long id, User user, CodefExAccountSnapshot snapshot) {
        ExAccount account = ExAccount.create(
                user,
                snapshot.organization(),
                ACCOUNT_NUMBER_HASH,
                "123***7890",
                snapshot.accountName(),
                snapshot.accountAlias(),
                snapshot.assetType(),
                snapshot.balance(),
                snapshot.withdrawableAmount(),
                snapshot.openedAt(),
                snapshot.maturityAt(),
                snapshot.lastTransactionAt()
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private User createUser(Long id) {
        User user = User.create(
                "user" + id + "@example.com",
                "password",
                "홍길동",
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
