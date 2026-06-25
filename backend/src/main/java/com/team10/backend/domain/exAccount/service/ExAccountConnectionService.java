package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountAuthException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountCryptoException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationFailure;
import com.team10.backend.domain.codef.exAccount.service.CodefExAccountGateway;
import com.team10.backend.domain.codef.exAccount.store.CodefExAccountCandidateStore;
import com.team10.backend.domain.exAccount.dto.res.ExAccountCandidateListRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountCandidateRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountConnectionRes;
import com.team10.backend.domain.exAccount.entity.ExAccountConnection;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import com.team10.backend.domain.exAccount.exception.ExAccountConnectionErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountConnectionRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExAccountConnectionService {
    private static final DateTimeFormatter CODEF_BIRTH_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd");

    private final UserRepository userRepository;
    private final ExAccountConnectionRepository connectionRepository;
    private final CodefExAccountGateway codefExAccountGateway;
    private final ExAccountSyncService exAccountSyncService;
    private final ExAccountRepository exAccountRepository;
    private final CodefExAccountCandidateStore candidateStore;
    private final ExAccountCodefRateLimitService rateLimitService;
    private final DistributedLockTemplate lockTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 사용자의 특정 금융기관 계정 인증 정보(connectedId)를 등록하고 DB에 저장(암호화)합니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExAccountConnectionRes register(
            Long userId,
            CodefExAccountConnectionCreateReq request
    ) {
        String lockKey = "lock:ex-account:connection:" + userId + ":" + request.organization();
        return lockTemplate.executeWithLock(
                lockKey,
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_CONCURRENT_REQUEST,
                () -> {
                    // 트랜잭션 외부에서 실행: Rate limit 체크 및 외부 API 호출 (커넥션 풀 고사 방지)
                    rateLimitService.checkRegister(userId, request.organization());
                    EncryptedConnectedId encryptedConnectedId = registerWithProvider(request);

                    // 트랜잭션 내부에서 실행: DB 조회 및 저장
                    return transactionTemplate.execute(status -> {
                        User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

                        ExAccountConnection connection = connectionRepository
                                .findByUserIdAndOrganization(userId, request.organization())
                                .map(existing -> updateConnection(existing, encryptedConnectedId))
                                .orElseGet(() -> ExAccountConnection.create(
                                        user,
                                        request.organization(),
                                        encryptedConnectedId
                                ));
                        ExAccountConnection saved = connectionRepository.saveAndFlush(connection);
                        return new ExAccountConnectionRes(saved.getOrganization(), saved.getStatus());
                    });
                }
        );
    }

    /**
     * 금융기관 연결 정보를 복호화해 CODEF API로부터 보유 계좌 후보군 목록을 실시간으로 가져옵니다.
     * 가져온 데이터는 검증 세션을 생성해 Redis에 안전히 캐싱합니다.
     */
    @Transactional
    public ExAccountCandidateListRes getLinkCandidates(Long userId, String organization) {
        // 1. 해당 기관의 연결 정보(ExAccountConnection)가 유효(ACTIVE)한 상태인지 확인
        ExAccountConnection connection = getActiveConnection(userId, organization);

        rateLimitService.checkAccountList(userId, organization);

        // 2. 암호화된 connectedId를 복호화하여 CODEF API로부터 실시간 보유 계좌 스냅샷 데이터 획득
        List<CodefExAccountSnapshot> snapshots = getAccountSnapshotsWithProvider(connection, organization);

        // 3. 커넥션의 최종 동기화 시각 업데이트
        connection.markSynced(LocalDateTime.now(ZoneOffset.UTC));

        // 계좌가 전혀 발견되지 않은 경우, 토큰 없이 빈 결과 반환
        if (snapshots.isEmpty()) {
            return new ExAccountCandidateListRes("", 300, List.of());
        }

        // 4. [중요 보안] 획득한 원본 데이터를 Redis에 임시 캐싱(TTL 5분)하고 일회용 candidateToken(UUID) 획득
        String candidateToken = candidateStore.save(userId, snapshots);

        // 5. 클라이언트 응답용 계좌 후보군 정보 빌드
        List<ExAccountCandidateRes> accounts = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            CodefExAccountSnapshot snapshot = snapshots.get(i);
            
            // 계좌 번호 암호 해싱(Blind Index) 및 마스킹 처리
            String hash = exAccountSyncService.getAccountNumberHash(snapshot.accountNumber());
            String masked = exAccountSyncService.getMaskedAccountNumber(snapshot.accountNumber());

            // 이미 연동(DB 등록)이 완료된 상태의 계좌인지 판별
            boolean linked = exAccountRepository
                    .findByUserIdAndOrganizationAndAccountNumberHash(userId, organization, hash)
                    .isPresent();

            // 인덱스 번호(i)와 연동 여부를 함께 실어 반환
            accounts.add(ExAccountCandidateRes.from(i, snapshot, masked, linked));
        }

        return new ExAccountCandidateListRes(candidateToken, 300, accounts);
    }

    /**
     * 연결 정보가 유효하고 가용한 상태(ACTIVE)인지 판별하는 헬퍼 메서드입니다.
     */
    private ExAccountConnection getActiveConnection(Long userId, String organization) {
        ExAccountConnection connection = connectionRepository
                .findByUserIdAndOrganization(userId, organization)
                .orElseThrow(() -> new BusinessException(
                        ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_NOT_FOUND));
        if (!connection.isActive()) {
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_INACTIVE);
        }
        return connection;
    }

    /**
     * 기존 연결 정보의 암호화된 connectedId를 교체 갱신합니다.
     */
    private ExAccountConnection updateConnection(
            ExAccountConnection existing,
            EncryptedConnectedId encryptedConnectedId
    ) {
        existing.replaceConnectedId(encryptedConnectedId);
        return existing;
    }

    private EncryptedConnectedId registerWithProvider(CodefExAccountConnectionCreateReq request) {
        try {
            return codefExAccountGateway.register(request);
        } catch (CodefExAccountRegistrationException exception) {
            throw new BusinessException(toErrorCode(exception), exception);
        } catch (CodefExAccountAuthException | CodefExAccountCryptoException | CodefExAccountClientException exception) {
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_UNAVAILABLE,
                    exception
            );
        }
    }

    private List<CodefExAccountSnapshot> getAccountSnapshotsWithProvider(
            ExAccountConnection connection,
            String organization
    ) {
        try {
            return codefExAccountGateway.getAccountSnapshots(
                    organization,
                    connection.encryptedConnectedId(),
                    connection.getUser().getBirthDate().format(CODEF_BIRTH_DATE_FORMATTER)
            );
        } catch (CodefExAccountAuthException | CodefExAccountCryptoException exception) {
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_UNAVAILABLE,
                    exception
            );
        } catch (CodefExAccountClientException exception) {
            throw new BusinessException(
                    ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_INVALID_RESPONSE,
                    exception
            );
        }
    }

    private ExAccountConnectionErrorCode toErrorCode(CodefExAccountRegistrationException exception) {
        CodefExAccountRegistrationFailure failure = exception.getFailure();
        if (failure == CodefExAccountRegistrationFailure.CREDENTIAL_INVALID) {
            return ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_CREDENTIAL_INVALID;
        }
        if (failure == CodefExAccountRegistrationFailure.ADDITIONAL_AUTH_REQUIRED) {
            return ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_ADDITIONAL_AUTH_REQUIRED;
        }
        if (failure == CodefExAccountRegistrationFailure.INVALID_RESPONSE) {
            return ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_INVALID_RESPONSE;
        }
        return ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_PROVIDER_UNAVAILABLE;
    }
}
