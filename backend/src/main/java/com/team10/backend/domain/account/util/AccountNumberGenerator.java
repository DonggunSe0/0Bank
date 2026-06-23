package com.team10.backend.domain.account.util;

import java.util.concurrent.ThreadLocalRandom;

public class AccountNumberGenerator {

    private static final String ACCOUNT_NUMBER_PREFIX = "0314";
    private static final int RANDOM_NUMBER_BOUND = 100_000_000;

    private AccountNumberGenerator() {}

    // 앞 4자리는 0314로 고정하고, 뒤 8자리는 랜덤 숫자로 생성한다.
    // 계좌번호는 계산 대상이 아니므로 String으로 반환한다.
    public static String generate() {
        int randomNumber = ThreadLocalRandom.current()
                .nextInt(RANDOM_NUMBER_BOUND);

        return ACCOUNT_NUMBER_PREFIX + String.format("%08d", randomNumber);
    }
}