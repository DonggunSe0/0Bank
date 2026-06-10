package com.team10.backend.domain.user.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 행안부 신분증 진위 확인 Mock 서비스.
 *
 * <p>실제 행안부 망 연동이 불가한 개발 환경에서
 * 실제 금융권 표준 명세를 기반으로 한 가상 검증 시나리오를 제공한다.
 *
 * <h2>Mock 시나리오</h2>
 * <ul>
 *   <li>정상 신분증 → {@link GovernmentVerifyResult#VERIFIED}</li>
 *   <li>발급일자 불일치 → {@link GovernmentVerifyResult#ISSUE_DATE_MISMATCH}
 *       (분실·도난 후 재발급된 신분증)</li>
 *   <li>존재하지 않는 명의 → {@link GovernmentVerifyResult#IDENTITY_NOT_FOUND}
 *       (위조 신분증)</li>
 *   <li>타임아웃 → {@link GovernmentVerifyTimeoutException}
 *       (행안부 서버 무응답 시뮬레이션)</li>
 * </ul>
 *
 * <p>실제 운영 전환 시 이 클래스를 실제 행안부 API 클라이언트로 교체한다.
 * 인터페이스({@code GovernmentVerifyService})를 두어 교체 비용을 최소화할 수 있다.
 */
@Slf4j
@Service
public class MockGovernmentVerifyService {

    /** 타임아웃 시뮬레이션 지연 시간 (ms) */
    private static final long TIMEOUT_DELAY_MS = 3_000L;

    /**
     * 발급일자 불일치 시나리오 주민등록번호 목록.
     * 분실 후 재발급된 신분증 — 입력된 발급일자가 정부 기록과 다른 케이스.
     */
    private static final Set<String> ISSUE_DATE_MISMATCH_IDS = Set.of(
            "900101-1111111",   // 테스트: 분실 신분증 A
            "850515-2222222"    // 테스트: 분실 신분증 B
    );

    /**
     * 존재하지 않는 명의 시나리오 주민등록번호 목록.
     * 실제로 등록되지 않은 번호 — 위조 신분증.
     */
    private static final Set<String> IDENTITY_NOT_FOUND_IDS = Set.of(
            "991231-3333333",   // 테스트: 위조 신분증 A
            "001010-4444444"    // 테스트: 위조 신분증 B
    );

    /**
     * 타임아웃 시나리오 주민등록번호 목록.
     * 행안부 서버 무응답 상황 시뮬레이션.
     */
    private static final Set<String> TIMEOUT_IDS = Set.of(
            "800101-1999999"    // 테스트: 타임아웃 트리거
    );

    /**
     * 신분증 진위를 확인한다.
     *
     * @param name           OCR 추출 이름
     * @param residentNumber OCR 추출 주민등록번호
     * @param issueDate      OCR 추출 발급일자 (yyyy-MM-dd)
     * @return 검증 결과
     * @throws GovernmentVerifyTimeoutException 행안부 응답 타임아웃 시
     */
    public GovernmentVerifyResult verify(String name, String residentNumber, String issueDate) {
        log.info("[GOV-MOCK] 진위 확인 요청 — residentNumber={}, issueDate={}", residentNumber, issueDate);

        // 타임아웃 시나리오: 인터럽트 가능 슬립으로 실제 네트워크 지연 모사
        if (TIMEOUT_IDS.contains(residentNumber)) {
            log.warn("[GOV-MOCK] 타임아웃 시나리오 트리거 — residentNumber={}", residentNumber);
            simulateTimeout(residentNumber);
        }

        // 발급일자 불일치 시나리오
        if (ISSUE_DATE_MISMATCH_IDS.contains(residentNumber)) {
            log.warn("[GOV-MOCK] 발급일자 불일치 — residentNumber={}", residentNumber);
            return GovernmentVerifyResult.ISSUE_DATE_MISMATCH;
        }

        // 존재하지 않는 명의 시나리오
        if (IDENTITY_NOT_FOUND_IDS.contains(residentNumber)) {
            log.warn("[GOV-MOCK] 존재하지 않는 명의 — residentNumber={}", residentNumber);
            return GovernmentVerifyResult.IDENTITY_NOT_FOUND;
        }

        log.info("[GOV-MOCK] 진위 확인 성공 — residentNumber={}", residentNumber);
        return GovernmentVerifyResult.VERIFIED;
    }

    private void simulateTimeout(String residentNumber) {
        try {
            Thread.sleep(TIMEOUT_DELAY_MS);
            // 슬립 후에도 타임아웃으로 처리
            throw new GovernmentVerifyTimeoutException(
                    "행안부 외부 API 응답 초과 (mock " + TIMEOUT_DELAY_MS + "ms): residentNumber=" + residentNumber
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GovernmentVerifyTimeoutException("행안부 연동 중 인터럽트 발생", e);
        }
    }
}
