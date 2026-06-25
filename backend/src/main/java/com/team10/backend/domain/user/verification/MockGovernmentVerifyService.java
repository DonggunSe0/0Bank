package com.team10.backend.domain.user.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 행안부 신분증 진위 확인 Mock 서비스 (개발 환경 전용).
 * 운영 전환 시 실제 행안부 API 클라이언트로 교체한다.
 */
@Slf4j
@Service
public class MockGovernmentVerifyService {

    private static final long TIMEOUT_DELAY_MS = 3_000L;

    // 발급일자 불일치 시나리오 — 분실·재발급 신분증 Mock
    private static final Set<String> ISSUE_DATE_MISMATCH_IDS = Set.of(
            "900101-1111111",   // 테스트: 분실 신분증 A
            "850515-2222222"    // 테스트: 분실 신분증 B
    );

    // 존재하지 않는 명의 시나리오 — 위조 신분증 Mock
    private static final Set<String> IDENTITY_NOT_FOUND_IDS = Set.of(
            "991231-3333333",   // 테스트: 위조 신분증 A
            "001010-4444444"    // 테스트: 위조 신분증 B
    );

    // 타임아웃 시나리오 트리거용 주민등록번호
    private static final Set<String> TIMEOUT_IDS = Set.of(
            "800101-1999999"    // 테스트: 타임아웃 트리거
    );

    public GovernmentVerifyResult verify(String name, String residentNumber, String issueDate) {
        log.info("[GOV-MOCK] 진위 확인 요청 — residentNumber={}, issueDate={}", mask(residentNumber), issueDate);

        if (TIMEOUT_IDS.contains(residentNumber)) {
            log.warn("[GOV-MOCK] 타임아웃 시나리오 트리거 — residentNumber={}", mask(residentNumber));
            simulateTimeout(residentNumber);
        }

        if (ISSUE_DATE_MISMATCH_IDS.contains(residentNumber)) {
            log.warn("[GOV-MOCK] 발급일자 불일치 — residentNumber={}", mask(residentNumber));
            return GovernmentVerifyResult.ISSUE_DATE_MISMATCH;
        }

        if (IDENTITY_NOT_FOUND_IDS.contains(residentNumber)) {
            log.warn("[GOV-MOCK] 존재하지 않는 명의 — residentNumber={}", mask(residentNumber));
            return GovernmentVerifyResult.IDENTITY_NOT_FOUND;
        }

        log.info("[GOV-MOCK] 진위 확인 성공 — residentNumber={}", mask(residentNumber));
        return GovernmentVerifyResult.VERIFIED;
    }

    /** 로그용 주민등록번호 마스킹 (앞 6자리만 표시) */
    private String mask(String residentNumber) {
        if (residentNumber == null || residentNumber.length() < 6) return "***";
        return residentNumber.substring(0, 6) + "-*******";
    }

    private void simulateTimeout(String residentNumber) {
        try {
            Thread.sleep(TIMEOUT_DELAY_MS);
            throw new GovernmentVerifyTimeoutException(
                    "행안부 외부 API 응답 초과 (mock " + TIMEOUT_DELAY_MS + "ms): residentNumber=" + mask(residentNumber)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GovernmentVerifyTimeoutException("행안부 연동 중 인터럽트 발생", e);
        }
    }
}
