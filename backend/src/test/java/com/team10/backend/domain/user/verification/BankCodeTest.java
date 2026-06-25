package com.team10.backend.domain.user.verification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BankCodeTest {

    @Nested
    @DisplayName("fromCode")
    class FromCode {

        @Test
        @DisplayName("등록된 기관코드 → 해당 BankCode 반환")
        void knownCode_returnsBankCode() {
            Optional<BankCode> result = BankCode.fromCode("004");

            assertThat(result).contains(BankCode.KB);
        }

        @Test
        @DisplayName("등록되지 않은 기관코드 → empty 반환")
        void unknownCode_returnsEmpty() {
            Optional<BankCode> result = BankCode.fromCode("999");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("isMaintenance")
    class IsMaintenance {

        @Test
        @DisplayName("점검 시작 시각 이후(자정 전) → 점검 중")
        void afterStartBeforeMidnight_isMaintenance() {
            assertThat(BankCode.KB.isMaintenance(LocalTime.of(23, 45))).isTrue();
        }

        @Test
        @DisplayName("자정을 넘어 점검 종료 시각 이전 → 점검 중")
        void afterMidnightBeforeEnd_isMaintenance() {
            assertThat(BankCode.KB.isMaintenance(LocalTime.of(0, 15))).isTrue();
        }

        @Test
        @DisplayName("점검 시간대 밖(낮 시간) → 점검 아님")
        void daytime_isNotMaintenance() {
            assertThat(BankCode.KB.isMaintenance(LocalTime.of(12, 0))).isFalse();
        }

        @Test
        @DisplayName("점검 종료 시각 정각 → 점검 종료로 취급")
        void atEndBoundary_isNotMaintenance() {
            assertThat(BankCode.KB.isMaintenance(LocalTime.of(0, 30))).isFalse();
        }

        @Test
        @DisplayName("점검 시작 시각 정각 → 점검 중으로 취급")
        void atStartBoundary_isMaintenance() {
            assertThat(BankCode.KB.isMaintenance(LocalTime.of(23, 30))).isTrue();
        }

        @Test
        @DisplayName("점검 시간이 없는 인터넷전문은행 → 항상 점검 아님")
        void noMaintenanceWindow_neverMaintenance() {
            assertThat(BankCode.KAKAO.isMaintenance(LocalTime.of(23, 45))).isFalse();
            assertThat(BankCode.KAKAO.isMaintenance(LocalTime.of(0, 15))).isFalse();
            assertThat(BankCode.KAKAO.isMaintenance(LocalTime.of(12, 0))).isFalse();
        }
    }
}
