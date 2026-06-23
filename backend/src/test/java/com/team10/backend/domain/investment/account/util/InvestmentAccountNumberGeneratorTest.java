package com.team10.backend.domain.investment.account.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvestmentAccountNumberGeneratorTest {

    @Test
    @DisplayName("투자 계좌번호는 10자리 숫자, 하이픈, 2자리 숫자로 구성된 13자리 문자열이다")
    void generate() {
        String accountNumber = InvestmentAccountNumberGenerator.generate();

        assertThat(accountNumber).hasSize(13);
        assertThat(accountNumber).matches("\\d{10}-\\d{2}");
    }
}
