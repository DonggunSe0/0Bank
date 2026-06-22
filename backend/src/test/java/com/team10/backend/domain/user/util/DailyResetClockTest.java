package com.team10.backend.domain.user.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DailyResetClock}이 "지금부터 다음 날 00:00(KST)까지 남은 초"를 올바르게 계산하는지 검증한다.
 * 실제 시계를 사용하므로(주입 가능한 Clock 없음 — IdentityVerificationService의
 * {@code LocalTime.now(ZoneId.of("Asia/Seoul"))}와 동일한 프로젝트 관례), 테스트 실행 사이의
 * 시간 오차를 감안해 약간의 허용 오차(delta)를 둔다.
 */
class DailyResetClockTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("1 이상 86400 이하의 값을 반환한다")
    void returnsValueWithinOneDay() {
        long seconds = DailyResetClock.secondsUntilNextMidnight();

        assertThat(seconds).isPositive();
        assertThat(seconds).isLessThanOrEqualTo(Duration.ofDays(1).toSeconds());
    }

    @Test
    @DisplayName("직접 계산한 '다음 자정(KST)까지 남은 초'와 거의 일치한다")
    void matchesIndependentlyComputedValue() {
        ZonedDateTime before = ZonedDateTime.now(SEOUL_ZONE);

        long actual = DailyResetClock.secondsUntilNextMidnight();

        ZonedDateTime expectedNextMidnight = before.toLocalDate().plusDays(1).atStartOfDay(SEOUL_ZONE);
        long expected = Duration.between(before, expectedNextMidnight).getSeconds();

        // 호출 사이에 흐른 시간만큼만 차이가 나야 하므로 2초 이내 오차를 허용한다.
        assertThat(Math.abs(actual - expected)).isLessThanOrEqualTo(2);
    }
}
