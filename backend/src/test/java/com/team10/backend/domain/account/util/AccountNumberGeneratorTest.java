package com.team10.backend.domain.account.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberGeneratorTest {

    @Test
    @DisplayName("계좌번호는 0314로 시작하는 12자리 문자열이다")
    void generate() {
        String accountNumber = AccountNumberGenerator.generate();

        assertThat(accountNumber).startsWith("0314");
        assertThat(accountNumber).hasSize(12);
        assertThat(accountNumber).containsOnlyDigits();
    }
}
