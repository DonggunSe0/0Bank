package com.team10.backend.domain.codef.exAccount.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountTransactionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodefExAccountTransactionMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CodefExAccountTransactionMapper mapper = new CodefExAccountTransactionMapper();

    @Test
    void fallbackTransactionKeyDoesNotExposeRawAccountNumber() throws Exception {
        String accountNumber = "1234567890";
        JsonNode data = objectMapper.readTree("""
                {
                  "resTrHistoryList": [{
                    "resAccountTrDate": "20260618",
                    "resAccountTrTime": "143000",
                    "resAccountOut": "45000",
                    "resAccountDesc1": "스타벅스"
                  }]
                }
                """);

        List<CodefExAccountTransactionSnapshot> snapshots =
                mapper.toSnapshots("0004", accountNumber, data);

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).transactionKey())
                .hasSize(64)
                .doesNotContain(accountNumber)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void skipsMalformedTransactionItemsAndKeepsValidItems() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resTrHistoryList": [
                    {
                      "resAccountTrDate": "bad-date",
                      "resAccountIn": "1000"
                    },
                    {
                      "resAccountTrDate": "2026.06.18",
                      "resAccountTrTime": "14:30:00",
                      "resAccountIn": "1,000",
                      "resAccountDesc1": "급여"
                    }
                  ]
                }
                """);

        List<CodefExAccountTransactionSnapshot> snapshots =
                mapper.toSnapshots("0004", "1234567890", data);

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).counterpartyName()).isEqualTo("급여");
        assertThat(snapshots.get(0).amount()).isEqualByComparingTo("1000");
    }

    @Test
    void infersWithdrawDirectionFromNegativeExplicitAmount() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resTrHistoryList": [{
                    "resAccountTrDate": "20260618143000",
                    "resAccountTrAmt": "-45000",
                    "resAccountDesc1": "카드결제"
                  }]
                }
                """);

        List<CodefExAccountTransactionSnapshot> snapshots =
                mapper.toSnapshots("0004", "1234567890", data);

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).direction().name()).isEqualTo("OUT");
        assertThat(snapshots.get(0).amount()).isEqualByComparingTo("45000");
    }

    @Test
    void fallbackTransactionKeyDoesNotDependOnResponseIndex() throws Exception {
        JsonNode firstResponse = objectMapper.readTree("""
                {
                  "resTrHistoryList": [
                    {
                      "resAccountTrDate": "20260618",
                      "resAccountTrTime": "143000",
                      "resAccountOut": "45000",
                      "resAfterTranBalance": "955000",
                      "resAccountDesc1": "스타벅스"
                    },
                    {
                      "resAccountTrDate": "20260619",
                      "resAccountTrTime": "090000",
                      "resAccountIn": "100000",
                      "resAfterTranBalance": "1055000",
                      "resAccountDesc1": "급여"
                    }
                  ]
                }
                """);
        JsonNode secondResponse = objectMapper.readTree("""
                {
                  "resTrHistoryList": [
                    {
                      "resAccountTrDate": "20260619",
                      "resAccountTrTime": "090000",
                      "resAccountIn": "100000",
                      "resAfterTranBalance": "1055000",
                      "resAccountDesc1": "급여"
                    },
                    {
                      "resAccountTrDate": "20260618",
                      "resAccountTrTime": "143000",
                      "resAccountOut": "45000",
                      "resAfterTranBalance": "955000",
                      "resAccountDesc1": "스타벅스"
                    }
                  ]
                }
                """);

        String firstKey = mapper.toSnapshots("0004", "1234567890", firstResponse).get(0).transactionKey();
        String secondKey = mapper.toSnapshots("0004", "1234567890", secondResponse).get(1).transactionKey();

        assertThat(secondKey).isEqualTo(firstKey);
    }
}
