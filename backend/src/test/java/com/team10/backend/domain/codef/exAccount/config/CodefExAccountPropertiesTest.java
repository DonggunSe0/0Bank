package com.team10.backend.domain.codef.exAccount.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static com.team10.backend.domain.codef.exAccount.config.CodefExAccountRestClientConfig.API_REST_CLIENT;
import static com.team10.backend.domain.codef.exAccount.config.CodefExAccountRestClientConfig.OAUTH_REST_CLIENT;

class CodefExAccountPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CodefExAccountConfig.class, CodefExAccountRestClientConfig.class)
            .withPropertyValues(
                    "codef.account-inquiry.service-type=DEMO",
                    "codef.account-inquiry.client-id=account-client-id",
                    "codef.account-inquiry.client-secret=account-client-secret",
                    "codef.account-inquiry.public-key=account-public-key",
                    "codef.account-inquiry.base-url=https://development.codef.io",
                    "codef.account-inquiry.account-create-path=/v1/account/create",
                    "codef.account-inquiry.account-list-path=/account-list",
                    "codef.account-inquiry.bank-transaction-path=/transaction-list",
                    "codef.connected-id-crypto.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                    "codef.connected-id-crypto.key-version=test-v1",
                    "codef.one-won-transfer.client-id=transfer-client-id",
                    "codef.one-won-transfer.client-secret=transfer-client-secret",
                    "codef.one-won-transfer.public-key=transfer-public-key"
            );

    @Test
    void bindsOnlyAccountInquiryProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CodefExAccountProperties.class);
            assertThat(context).hasSingleBean(CodefConnectedIdCryptoProperties.class);
            assertThat(context).hasBean(OAUTH_REST_CLIENT);
            assertThat(context).hasBean(API_REST_CLIENT);

            CodefExAccountProperties properties = context.getBean(CodefExAccountProperties.class);
            assertThat(properties.serviceType()).isEqualTo("DEMO");
            assertThat(properties.clientId()).isEqualTo("account-client-id");
            assertThat(properties.clientSecret()).isEqualTo("account-client-secret");
            assertThat(properties.publicKey()).isEqualTo("account-public-key");
            assertThat(properties.baseUrl()).isEqualTo("https://development.codef.io");
            assertThat(properties.accountCreatePath()).isEqualTo("/v1/account/create");
            assertThat(properties.accountListPath()).isEqualTo("/account-list");
            assertThat(properties.bankTransactionPath()).isEqualTo("/transaction-list");
            assertThat(properties.toString())
                    .doesNotContain("account-client-id")
                    .doesNotContain("account-client-secret")
                    .doesNotContain("account-public-key");
            CodefConnectedIdCryptoProperties cryptoProperties =
                    context.getBean(CodefConnectedIdCryptoProperties.class);
            assertThat(cryptoProperties.keyVersion()).isEqualTo("test-v1");
            assertThat(cryptoProperties.toString())
                    .doesNotContain(cryptoProperties.secretKey());
        });
    }

    @Test
    void failsToStartWhenRequiredSecretIsMissing() {
        assertMissingPropertyFails("client-id");
        assertMissingPropertyFails("client-secret");
        assertMissingPropertyFails("public-key");
        assertMissingPropertyFails("base-url");
        contextRunner
                .withPropertyValues("codef.connected-id-crypto.secret-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    private void assertMissingPropertyFails(String propertyName) {
        contextRunner
                .withPropertyValues("codef.account-inquiry." + propertyName + "=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Could not bind properties");
                });
    }
}
