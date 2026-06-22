package com.team10.backend.domain.user.util;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** "하루 한도" Redis 카운터를 KST 자정 기준으로 리셋하기 위한 TTL 계산 유틸리티. 최소 1초를 보장한다. */
public final class DailyResetClock {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private DailyResetClock() {
    }

    /** 지금부터 다음 날 00:00(KST)까지 남은 초. 항상 1 이상을 반환한다. */
    public static long secondsUntilNextMidnight() {
        ZonedDateTime now = ZonedDateTime.now(SEOUL_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(SEOUL_ZONE);
        return Math.max(Duration.between(now, nextMidnight).getSeconds(), 1);
    }
}
