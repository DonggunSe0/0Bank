package com.team10.backend.domain.codef.exAccount.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodefExAccountSnapshotMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CodefExAccountSnapshotMapper mapper = new CodefExAccountSnapshotMapper();

    @Test
    void normalizesSingleAndMultipleAccountGroups() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resDepositTrust": [
                    {
                      "resAccount": "111122223333",
                      "resAccountName": "입출금통장",
                      "resAccountNickName": "생활비",
                      "resAccountDeposit": "11",
                      "resAccountBalance": "1,500,000",
                      "resWithdrawalAmt": "1,200,000",
                      "resAccountStartDate": "20240115",
                      "resAccountEndDate": "",
                      "resLastTranDate": "20260618"
                    },
                    {
                      "resAccount": "444455556666",
                      "resAccountName": "정기적금",
                      "resAccountDeposit": "12",
                      "resAccountBalance": "3000000"
                    },
                    {
                      "resAccountName": "계좌번호 누락 항목",
                      "resAccountDeposit": "10",
                      "resAccountBalance": "0"
                    }
                  ],
                  "resForeignCurrency": {
                    "resAccount": "FX-001",
                    "resAccountName": "달러예금",
                    "resAccountDeposit": "20",
                    "resAccountBalance": "125.50"
                  },
                  "resFund": [{
                    "resAccount": "FUND-001",
                    "resAccountName": "인덱스펀드",
                    "resAccountDeposit": "30",
                    "resAccountBalance": "550000"
                  }],
                  "resLoan": {
                    "resAccount": "LOAN-001",
                    "resAccountName": "신용대출",
                    "resAccountDeposit": "40",
                    "resAccountBalance": "10000000",
                    "resAccountEndDate": "2027-01-15"
                  },
                  "resInsurance": {
                    "resAccount": "INS-001",
                    "resAccountName": "청년보험",
                    "resAccountDeposit": "50",
                    "resAccountBalance": "100000"
                  }
                }
                """);

        List<CodefExAccountSnapshot> snapshots = mapper.toSnapshots("0020", data);

        assertThat(snapshots).hasSize(6);
        assertThat(snapshot(snapshots, "111122223333"))
                .satisfies(account -> {
                    assertThat(account.organization()).isEqualTo("0020");
                    assertThat(account.assetType()).isEqualTo(ExAccountType.DEMAND);
                    assertThat(account.balance()).isEqualByComparingTo(new BigDecimal("1500000"));
                    assertThat(account.withdrawableAmount()).isEqualByComparingTo(new BigDecimal("1200000"));
                    assertThat(account.openedAt()).isEqualTo(LocalDate.of(2024, 1, 15));
                    assertThat(account.maturityAt()).isNull();
                    assertThat(account.lastTransactionAt()).isEqualTo(LocalDate.of(2026, 6, 18));
                });
        assertThat(snapshot(snapshots, "444455556666").assetType()).isEqualTo(ExAccountType.SAVING);
        assertThat(snapshot(snapshots, "FX-001").assetType()).isEqualTo(ExAccountType.FX);
        assertThat(snapshot(snapshots, "FUND-001").assetType()).isEqualTo(ExAccountType.FUND);
        assertThat(snapshot(snapshots, "LOAN-001").assetType()).isEqualTo(ExAccountType.LOAN);
        assertThat(snapshot(snapshots, "INS-001").assetType()).isEqualTo(ExAccountType.INSURANCE);
    }

    private CodefExAccountSnapshot snapshot(
            List<CodefExAccountSnapshot> snapshots,
            String accountNumber
    ) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.accountNumber().equals(accountNumber))
                .findFirst()
                .orElseThrow();
    }
}
