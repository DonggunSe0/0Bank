package com.team10.backend.domain.codef.exAccount.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CodefExAccountSnapshotMapper {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<String, ExAccountType> RESPONSE_TYPES = Map.of(
            "resDepositTrust", ExAccountType.UNKNOWN,
            "resForeignCurrency", ExAccountType.FX,
            "resFund", ExAccountType.FUND,
            "resLoan", ExAccountType.LOAN,
            "resInsurance", ExAccountType.INSURANCE
    );

    public List<CodefExAccountSnapshot> toSnapshots(String organization, JsonNode data) {
        if (organization == null || organization.isBlank() || data == null || !data.isContainerNode()) {
            throw new CodefExAccountClientException("CODEF 보유계좌 데이터가 올바르지 않습니다.");
        }

        List<CodefExAccountSnapshot> snapshots = new ArrayList<>();
        RESPONSE_TYPES.forEach((fieldName, defaultType) ->
                appendSnapshots(snapshots, organization, data.path(fieldName), fieldName, defaultType));
        return List.copyOf(snapshots);
    }

    private void appendSnapshots(
            List<CodefExAccountSnapshot> snapshots,
            String organization,
            JsonNode accounts,
            String responseType,
            ExAccountType defaultType
    ) {
        if (accounts.isMissingNode() || accounts.isNull()) {
            return;
        }
        if (accounts.isArray()) {
            accounts.forEach(account -> appendSnapshot(
                    snapshots, organization, account, responseType, defaultType));
            return;
        }
        if (accounts.isObject()) {
            appendSnapshot(snapshots, organization, accounts, responseType, defaultType);
        }
    }

    private void appendSnapshot(
            List<CodefExAccountSnapshot> snapshots,
            String organization,
            JsonNode account,
            String responseType,
            ExAccountType defaultType
    ) {
        String accountNumber = text(account, "resAccount");
        if (accountNumber == null) {
            log.warn("[CODEF] 계좌번호가 없는 보유계좌 항목을 제외했습니다. responseType={}", responseType);
            return;
        }

        snapshots.add(new CodefExAccountSnapshot(
                organization,
                accountNumber,
                text(account, "resAccountName"),
                text(account, "resAccountNickName"),
                resolveType(defaultType, text(account, "resAccountDeposit")),
                decimal(account, "resAccountBalance"),
                firstDecimal(account, "resWithdrawalAmt", "resWithdrawableAmount"),
                date(account, "resAccountStartDate"),
                date(account, "resAccountEndDate"),
                date(account, "resLastTranDate")
        ));
    }

    private ExAccountType resolveType(ExAccountType defaultType, String depositType) {
        if (defaultType != ExAccountType.UNKNOWN) {
            return defaultType;
        }
        if ("11".equals(depositType)) {
            return ExAccountType.DEMAND;
        }
        if ("12".equals(depositType) || "13".equals(depositType) || "14".equals(depositType)) {
            return ExAccountType.SAVING;
        }
        return ExAccountType.UNKNOWN;
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new CodefExAccountClientException("CODEF 보유계좌 금액 형식이 올바르지 않습니다.", exception);
        }
    }

    private BigDecimal firstDecimal(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (text(node, fieldName) != null) {
                return decimal(node, fieldName);
            }
        }
        return null;
    }

    private LocalDate date(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null) {
            return null;
        }
        try {
            if (value.indexOf('-') >= 0) {
                return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            }
            return LocalDate.parse(value, BASIC_DATE);
        } catch (DateTimeException exception) {
            throw new CodefExAccountClientException("CODEF 보유계좌 날짜 형식이 올바르지 않습니다.", exception);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isValueNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
