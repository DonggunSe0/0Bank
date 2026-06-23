package com.team10.backend.domain.user.verification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** CODEF 기관코드 및 은행별 점검 시간 매핑. */
@Getter
@RequiredArgsConstructor
public enum BankCode {

    // ── 시중은행 ──────────────────────────────────────────────────────────────
    IBK        ("003", "기업은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    KB         ("004", "국민은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    SUHYUP     ("007", "수협은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    NH         ("011", "농협은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    WOORI      ("020", "우리은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    SC         ("023", "SC제일은행", LocalTime.of(23, 30), LocalTime.of(0, 30)),
    CITI       ("027", "씨티은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    DAEGU      ("031", "대구은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    BUSAN      ("032", "부산은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    GWANGJU    ("034", "광주은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    JEJU       ("035", "제주은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    JEONBUK    ("037", "전북은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    GYEONGNAM  ("039", "경남은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    SHINHAN    ("088", "신한은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    HANA       ("081", "하나은행",   LocalTime.of(23, 30), LocalTime.of(0, 30)),

    // ── 인터넷전문은행 (점검 없음) ────────────────────────────────────────────
    K_BANK     ("089", "케이뱅크",   null, null),
    KAKAO      ("090", "카카오뱅크", null, null),
    TOSS       ("092", "토스뱅크",   null, null),

    // ── 저축은행 ──────────────────────────────────────────────────────────────
    SBI        ("050", "SBI저축은행", LocalTime.of(23, 30), LocalTime.of(0, 30)),

    // ── 증권사 ────────────────────────────────────────────────────────────────
    SAMSUNG_SEC("240", "삼성증권",   LocalTime.of(23, 30), LocalTime.of(0, 30)),
    MIRAE_SEC  ("238", "미래에셋증권", LocalTime.of(23, 30), LocalTime.of(0, 30)),
    NH_SEC     ("247", "NH투자증권", LocalTime.of(23, 30), LocalTime.of(0, 30)),
    ;

    /** CODEF API에 전달하는 기관코드 */
    private final String code;
    private final String displayName;

    /**
     * 점검 시작 시각. null이면 점검 없음.
     * 자정을 넘는 구간(예: 23:30 ~ 00:30)을 지원하기 위해 종료 시각과 함께 판단한다.
     */
    private final LocalTime maintenanceStart;
    /** 점검 종료 시각. null이면 점검 없음. */
    private final LocalTime maintenanceEnd;

    // 코드 → BankCode 조회용 캐시. 호출마다 enum 전체를 선형 스캔하지 않도록 한 번만 구성해 둔다.
    private static final Map<String, BankCode> CODE_INDEX =
            Arrays.stream(values()).collect(Collectors.toMap(BankCode::getCode, Function.identity()));

    /** CODEF 기관코드로 BankCode를 조회한다(없으면 empty). */
    public static Optional<BankCode> fromCode(String code) {
        return Optional.ofNullable(CODE_INDEX.get(code));
    }

    /** 주어진 시각이 점검 시간대인지 확인(자정 넘는 구간 처리). */
    public boolean isMaintenance(LocalTime time) {
        if (maintenanceStart == null || maintenanceEnd == null) {
            return false;
        }
        if (maintenanceStart.isBefore(maintenanceEnd)) {
            // 같은 날 안에서 끝나는 구간 (예: 02:00 ~ 04:00)
            return !time.isBefore(maintenanceStart) && time.isBefore(maintenanceEnd);
        } else {
            // 자정을 넘는 구간 (예: 23:30 ~ 00:30)
            return !time.isBefore(maintenanceStart) || time.isBefore(maintenanceEnd);
        }
    }
}
