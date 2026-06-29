package com.team10.backend.domain.codef.exAccount.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountTransactionSnapshot;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.exAccount.type.ExAccountTransactionDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class CodefExAccountTransactionMapper {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASIC_TIME = DateTimeFormatter.ofPattern("HHmmss");

    public List<CodefExAccountTransactionSnapshot> toSnapshots(
            String organization,
            String accountNumber,
            JsonNode data
    ) {
        if (organization == null || organization.isBlank()
                || accountNumber == null || accountNumber.isBlank()
                || data == null || !data.isContainerNode()) {
            throw new CodefExAccountClientException("CODEF 거래내역 데이터가 올바르지 않습니다.");
        }

        JsonNode transactions = firstArray(
                data,
                "resTrHistoryList",
                "resAccountTrHistoryList",
                "resAccountTransactionList",
                "resAccountTrList",
                "resTransactionList",
                "resBankTransactionList",
                "resHistoryList",
                "resList",
                "transactionList"
        );
        if (transactions == null) {
            return List.of();
        }

        List<CodefExAccountTransactionSnapshot> snapshots = new ArrayList<>();
        int index = 0;
        for (JsonNode transaction : transactions) {
            appendSnapshot(snapshots, organization, accountNumber, transaction, index++);
        }
        return List.copyOf(snapshots);
    }

    private void appendSnapshot(
            List<CodefExAccountTransactionSnapshot> snapshots,
            String organization,
            String accountNumber,
            JsonNode transaction,
            int index
    ) {
        try {
            LocalDateTime transactedAt = dateTime(transaction);
            if (transactedAt == null) {
                log.warn("[CODEF] 거래일시가 없는 거래내역 항목을 제외했습니다. index={}", index);
                return;
            }

            BigDecimal inAmount = firstDecimal(transaction, "resAccountIn", "resDepositAmt", "resIn", "depositAmount");
            BigDecimal outAmount = firstDecimal(transaction, "resAccountOut", "resWithdrawAmt", "resWithdrawalAmt", "resOut", "withdrawAmount");
            BigDecimal explicitAmount = firstSignedDecimal(transaction, "resAccountTrAmt", "resTransactionAmount", "resAmount", "amount");

            ExAccountTransactionDirection direction = direction(transaction, inAmount, outAmount, explicitAmount);
            BigDecimal amount = amount(direction, inAmount, outAmount, explicitAmount);
            if (direction == null || amount == null) {
                log.warn("[CODEF] 입출금 방향 또는 금액이 없는 거래내역 항목을 제외했습니다. index={}", index);
                return;
            }

            String counterpartyName = firstText(
                    transaction,
                    "resAccountDesc1",
                    "resAccountDesc2",
                    "resAccountTrDesc",
                    "resMemberStoreName",
                    "resContent",
                    "resRemark"
            );
            String memo = firstText(transaction, "resAccountDesc3", "resMemo", "memo");
            String rawCategory = firstText(transaction, "resAccountTrType", "resTransactionType", "resCategory", "category");
            String transactionKey = firstText(transaction, "resAccountTrNo", "resTransactionId", "transactionKey", "resSeq");
            BigDecimal balanceAfter = firstDecimal(transaction, "resAfterTranBalance", "resAccountBalance", "resBalance", "balanceAfter");
            if (transactionKey == null) {
                transactionKey = fallbackKey(
                        organization,
                        accountNumber,
                        transactedAt,
                        direction,
                        amount,
                        balanceAfter,
                        counterpartyName,
                        memo,
                        rawCategory
                );
            }

            snapshots.add(new CodefExAccountTransactionSnapshot(
                    transactionKey,
                    transactedAt,
                    direction,
                    amount,
                    balanceAfter,
                    counterpartyName,
                    memo,
                    rawCategory
            ));
        } catch (CodefExAccountClientException exception) {
            log.warn("[CODEF] 거래내역 항목을 해석할 수 없어 제외했습니다. index={}, reason={}", index, exception.getMessage());
        }
    }

    private JsonNode firstArray(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isArray()) {
                return value;
            }
        }
        return node.isArray() ? node : null;
    }

    private LocalDateTime dateTime(JsonNode node) {
        String date = firstText(node, "resAccountTrDate", "resTrDate", "resTransactionDate", "resUsedDate", "date");
        if (date == null) {
            return null;
        }
        String time = firstText(node, "resAccountTrTime", "resTrTime", "resTransactionTime", "resUsedTime", "time");
        try {
            LocalDate localDate = parseDate(date);
            LocalTime localTime = time == null ? parseTimeFromDate(date) : parseTime(time);
            return LocalDateTime.of(localDate, localTime);
        } catch (DateTimeException exception) {
            throw new CodefExAccountClientException("CODEF 거래내역 일시 형식이 올바르지 않습니다.", exception);
        }
    }

    private LocalDate parseDate(String value) {
        String normalized = value.replace(".", "").replace("/", "").replace("-", "");
        if (normalized.length() >= 8) {
            return LocalDate.parse(normalized.substring(0, 8), BASIC_DATE);
        }
        if (value.indexOf('-') >= 0) {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return LocalDate.parse(value, BASIC_DATE);
    }

    private LocalTime parseTime(String value) {
        String normalized = value.replace(":", "").replace(".", "").replace("/", "").replace("-", "");
        if (normalized.length() >= 14) {
            normalized = normalized.substring(8, 14);
        }
        if (normalized.length() == 4) {
            normalized += "00";
        }
        if (normalized.length() > 6) {
            normalized = normalized.substring(0, 6);
        }
        return LocalTime.parse(normalized, BASIC_TIME);
    }

    private LocalTime parseTimeFromDate(String value) {
        String normalized = value.replace(":", "").replace(".", "").replace("/", "").replace("-", "");
        return normalized.length() >= 14 ? parseTime(normalized) : LocalTime.MIDNIGHT;
    }

    private ExAccountTransactionDirection direction(
            JsonNode node,
            BigDecimal inAmount,
            BigDecimal outAmount,
            BigDecimal explicitAmount
    ) {
        String value = firstText(node, "direction", "resDirection", "resAccountTrType", "resTransactionType");
        if (value != null) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.equals("IN") || normalized.equals("1")
                    || normalized.contains("입금") || normalized.contains("DEPOSIT")) {
                return ExAccountTransactionDirection.IN;
            }
            if (normalized.equals("OUT") || normalized.equals("2")
                    || normalized.contains("출금") || normalized.contains("WITHDRAW")) {
                return ExAccountTransactionDirection.OUT;
            }
        }
        if (inAmount != null && inAmount.compareTo(BigDecimal.ZERO) > 0) {
            return ExAccountTransactionDirection.IN;
        }
        if (outAmount != null && outAmount.compareTo(BigDecimal.ZERO) > 0) {
            return ExAccountTransactionDirection.OUT;
        }
        if (explicitAmount != null && explicitAmount.signum() < 0) {
            return ExAccountTransactionDirection.OUT;
        }
        return null;
    }

    private BigDecimal amount(
            ExAccountTransactionDirection direction,
            BigDecimal inAmount,
            BigDecimal outAmount,
            BigDecimal explicitAmount
    ) {
        if (direction == ExAccountTransactionDirection.IN && inAmount != null) {
            return inAmount;
        }
        if (direction == ExAccountTransactionDirection.OUT && outAmount != null) {
            return outAmount;
        }
        return explicitAmount == null ? null : explicitAmount.abs();
    }

    private BigDecimal firstDecimal(JsonNode node, String... fieldNames) {
        BigDecimal value = firstSignedDecimal(node, fieldNames);
        return value == null ? null : value.abs();
    }

    private BigDecimal firstSignedDecimal(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value == null) {
                continue;
            }
            try {
                String normalized = value.replace(",", "").replace("원", "").trim();
                if (normalized.equals("-") || normalized.equals("+")) {
                    continue;
                }
                return new BigDecimal(normalized);
            } catch (NumberFormatException exception) {
                throw new CodefExAccountClientException("CODEF 거래내역 금액 형식이 올바르지 않습니다.", exception);
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isValueNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String fallbackKey(
            String organization,
            String accountNumber,
            LocalDateTime transactedAt,
            ExAccountTransactionDirection direction,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String counterpartyName,
            String memo,
            String rawCategory
    ) {
        String rawKey = organization + "-" + accountNumber + "-" + transactedAt + "-"
                + direction + "-" + amount.toPlainString() + "-"
                + (balanceAfter == null ? "" : balanceAfter.toPlainString()) + "-"
                + normalizedKeyPart(counterpartyName) + "-"
                + normalizedKeyPart(memo) + "-"
                + normalizedKeyPart(rawCategory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new CodefExAccountClientException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private String normalizedKeyPart(String value) {
        return value == null ? "" : value.trim();
    }
}
