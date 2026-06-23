package com.team10.backend.domain.investment.account.util;

import java.util.concurrent.ThreadLocalRandom;

public class InvestmentAccountNumberGenerator {

    private static final long FRONT_BOUND = 10_000_000_000L;
    private static final int BACK_BOUND = 100;

    private InvestmentAccountNumberGenerator() {
    }

    /**
     * 단순 Random 사용 시 멀티 스레드 상황에서 Lock 경합이 발생할 수 있기에 스레드 단위의 ThreadLocalRandom을 사용한다
     */
    public static String generate() {
        long front = ThreadLocalRandom.current().nextLong(FRONT_BOUND);
        int back = ThreadLocalRandom.current().nextInt(BACK_BOUND);

        return String.format("%010d-%02d", front, back);
    }
}
