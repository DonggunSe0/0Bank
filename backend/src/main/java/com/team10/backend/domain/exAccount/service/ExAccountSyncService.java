package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.store.CodefExAccountCandidateStore;
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
import com.team10.backend.global.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExAccountSyncService {

    private static final int MAX_VISIBLE_PREFIX_LENGTH = 6;
    private static final int MAX_VISIBLE_SUFFIX_LENGTH = 4;
    private static final int MIN_MASK_LENGTH = 3;

    private final ExAccountRepository exAccountRepository;
    private final UserRepository userRepository;
    private final HmacSha256Hasher hmacSha256Hasher;
    private final CodefExAccountCandidateStore candidateStore;
    private final DistributedLockTemplate lockTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 사용자가 선택한 외부 계좌들을 안전하게 RDB에 연동(저장)합니다.
     * 
     * [주요 흐름]
     * 1. 클라이언트가 보낸 일회용 'candidateToken'으로 Redis에서 원본 CODEF 조회 스냅샷 목록을 꺼냅니다.
     * 2. 사용자가 선택한 인덱스 목록('selectedIndexes')이 유효한 범위(0 ~ 원본 개수-1) 내에 있는지 무결성 검증을 수행합니다.
     * 3. 검증된 원본 데이터를 바탕으로 DB에 Upsert(신규 저장 또는 기존 정보 갱신)를 수행합니다.
     * 4. claim에 성공한 토큰은 성공/실패와 관계없이 Redis에서 즉시 파기합니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ExAccountRes> linkAccounts(Long userId, ExAccountLinkReq request) {
        // 1. 요청 파라미터(토큰 유무 등) 기본 유효성 검사
        if (request == null || request.candidateToken() == null || request.candidateToken().isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        String lockKey = "lock:ex-account:sync:" + userId;
        return lockTemplate.executeWithLock(
                lockKey,
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                ExAccountErrorCode.EX_ACCOUNT_CONCURRENT_SYNC,
                () -> {
                    Optional<String> claimId = candidateStore.claim(userId, request.candidateToken());
                    if (claimId.isEmpty()) {
                        throw new BusinessException(ExAccountErrorCode.EX_ACCOUNT_CANDIDATE_ALREADY_CLAIMED);
                    }

                    try {
                        return transactionTemplate.execute(status -> linkClaimedAccounts(userId, request));
                    } finally {
                        candidateStore.remove(userId, request.candidateToken());
                        candidateStore.releaseClaim(userId, request.candidateToken(), claimId.get());
                    }
                }
        );
    }

    private List<ExAccountRes> linkClaimedAccounts(Long userId, ExAccountLinkReq request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // 2. Redis 캐시 저장소에서 유저 고유의 원본 CODEF 스냅샷 리스트를 조회
        List<CodefExAccountSnapshot> snapshots = candidateStore.get(userId, request.candidateToken());
        if (snapshots.isEmpty()) {
            // 캐시가 유실되었거나(5분 TTL 초과), 존재하지 않는 잘못된 토큰인 경우 예외 발생
            throw new BusinessException(ExAccountErrorCode.EX_ACCOUNT_CANDIDATE_NOT_FOUND);
        }

        List<ExAccountRes> results = new ArrayList<>();
        // 3. 사용자가 프론트엔드 화면에서 체크박스로 선택한 인덱스 목록(selectedIndexes)을 차례대로 연동 처리
        for (int index : request.selectedIndexes()) {
            // 인덱스가 Redis에 저장된 원본 데이터의 크기를 벗어난 경우 (악의적인 인덱스 변조 공격 방어)
            if (index < 0 || index >= snapshots.size()) {
                throw new BusinessException(ExAccountErrorCode.EX_ACCOUNT_CANDIDATE_INVALID_INDEX);
            }
            // 검증이 통과된 원본 스냅샷 획득
            CodefExAccountSnapshot snapshot = snapshots.get(index);
            // DB 적재 (신규 저장 또는 스냅샷 갱신)
            ExAccount account = upsertAccount(userId, snapshot);
            results.add(ExAccountRes.from(account));
        }
        return results;
    }

    /**
     * 외부 계좌번호의 마스킹된 버전을 반환합니다.
     * (예: "123-456-789" -> "12345*****6789")
     */
    public String getMaskedAccountNumber(String accountNumber) {
        return maskAccountNumber(normalizeAccountNumber(accountNumber));
    }

    /**
     * 계좌번호를 암호화 해싱하여 Blind Index(동일 계좌 조회용 해시)를 반환합니다.
     */
    public String getAccountNumberHash(String accountNumber) {
        return hmacSha256Hasher.hash(normalizeAccountNumber(accountNumber));
    }

    /**
     * 단일 계좌 정보를 데이터베이스에 반영(Insert 또는 Update)합니다.
     */
    private ExAccount upsertAccount(Long userId, CodefExAccountSnapshot snapshot) {
        // 계좌번호 보안 처리 (HMAC 해싱 해시값 & 화면 표시용 마스킹 값 생성)
        ProtectedAccountNumber accountNumber = protectAccountNumber(snapshot.accountNumber());

        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        exAccountRepository.upsert(
                userId,
                snapshot.organization(),
                accountNumber.hash(),
                accountNumber.masked(),
                snapshot.accountName(),
                snapshot.accountAlias(),
                snapshot.assetType().name(),
                snapshot.balance() == null ? java.math.BigDecimal.ZERO : snapshot.balance(),
                snapshot.withdrawableAmount(),
                snapshot.openedAt(),
                snapshot.maturityAt(),
                snapshot.lastTransactionAt(),
                "ACTIVE",
                now,
                now
        );

        return exAccountRepository.findByUserIdAndOrganizationAndAccountNumberHash(
                userId,
                snapshot.organization(),
                accountNumber.hash()
        ).orElseThrow(() -> new BusinessException(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND));
    }

    private void updateAccountSnapshot(ExAccount exAccount, CodefExAccountSnapshot snapshot) {
        exAccount.updateSnapshot(
                snapshot.accountName(),
                snapshot.accountAlias(),
                snapshot.balance(),
                snapshot.withdrawableAmount(),
                snapshot.maturityAt(),
                snapshot.lastTransactionAt()
        );
    }

    /**
     * 계좌번호를 정규화하고 보안 처리(해싱 및 마스킹)를 수행합니다.
     */
    private ProtectedAccountNumber protectAccountNumber(String accountNumber) {
        String normalized = normalizeAccountNumber(accountNumber);
        return new ProtectedAccountNumber(
                hmacSha256Hasher.hash(normalized),
                maskAccountNumber(normalized)
        );
    }

    /**
     * 계좌번호 내의 공백이나 하이픈(-) 기호를 제거하여 포맷을 단일화(정규화)합니다.
     * 동일 계좌임에도 표기법 차이로 중복 저장되는 현상을 예방합니다.
     */
    private String normalizeAccountNumber(String accountNumber) {
        return accountNumber.replace(" ", "").replace("-", "");
    }

    /**
     * 계좌번호 노출을 막기 위해 중간 부분을 별표(*) 기호로 덮어씌웁니다.
     */
    private String maskAccountNumber(String accountNumber) {
        int length = accountNumber.length();
        // 너무 짧은 계좌번호는 전체 별표 처리
        if (length <= MAX_VISIBLE_SUFFIX_LENGTH) {
            return "*".repeat(length);
        }

        int suffixLength = Math.min(MAX_VISIBLE_SUFFIX_LENGTH, length - MIN_MASK_LENGTH);
        int prefixLength = Math.min(
                MAX_VISIBLE_PREFIX_LENGTH,
                length - suffixLength - MIN_MASK_LENGTH
        );
        String prefix = accountNumber.substring(0, prefixLength);
        String suffix = accountNumber.substring(length - suffixLength);
        int maskLength = length - prefixLength - suffixLength;

        return prefix + "*".repeat(maskLength) + suffix;
    }

    /**
     * 보안 처리된 계좌번호의 결과 튜플 레코드입니다.
     */
    private record ProtectedAccountNumber(String hash, String masked) {
    }
}
