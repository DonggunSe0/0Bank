package com.team10.backend.domain.investment.client.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.domain.investment.client.stock.dto.KisStockMasterRow;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KisStockMasterFileParserTest {

    private static final Charset KIS_FILE_CHARSET = Charset.forName("MS949");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int[] OFFICIAL_PART2_FIELD_WIDTHS = {
            2, 1, 4, 4, 4,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 1, 1, 1, 1,
            1, 9, 5, 5, 1,
            1, 1, 2, 1, 1,
            1, 2, 2, 2, 3,
            1, 3, 12, 12, 8,
            15, 21, 2, 7, 1,
            1, 1, 1, 1, 9,
            9, 9, 5, 9, 8,
            9, 3, 1, 1, 1
    };
    private static final int OFFICIAL_PART2_BYTE_LENGTH = Arrays.stream(OFFICIAL_PART2_FIELD_WIDTHS).sum();
    private static final int TRHT_YN_FIELD = 34;
    private static final int SLTR_YN_FIELD = 35;
    private static final int MANG_ISSU_YN_FIELD = 36;
    private static final int PRDY_VOL_FIELD = 47;
    private static final int STCK_LSTN_DATE_FIELD = 49;
    private static final int CPFN_FIELD = 51;
    private static final int SALE_ACCOUNT_FIELD = 59;
    private static final int THTR_NTIN_FIELD = 62;
    private static final int PRDY_AVLS_SCAL_FIELD = 65;

    private final KisStockMasterFileParser parser = new KisStockMasterFileParser();

    @Test
    @DisplayName("실제 마스터 파일 일부 라인을 원본 고정폭 필드 기준으로 검증한다")
    void parseRealSampleLines() throws IOException {
        List<byte[]> sampleLines = List.of(
                line("F70100026KR5701000261한투글로벌넥스트웨이브1(A)              BC 000000000000NN 0NNN NN    N  0  N     0000010820000100000NNN00NNN000000100N09000000000000000000000100020250901000000000023788000000000023788084000         0 NNN00000000000000000000000000000000000000.00        000000257   NNN"),
                line("000100   KR7000100008유한양행                                ST1002700090000 NNAYNY YYNNYNNNN0NNNNNNNN0000764000000100001NNN00NNN000000060Y0900000004014500000000010001962110100000000007964700000000008020906400012       0 NYY00000526800000008800000028800234000009.7520260331000060850   NNY"),
                line("Q760018  KRG760000189키움 미국달러선물 ETN B                 EN 000000000000NN 0NNN3NN    N  0  N     0000111750000100001NNN00NNN000000100N09000000000000100000000000020250411000000000006000000000000000000000000         0 NNN00000000000000000000000000000000000000.00        000000670   NNN"),
                line("900140   KYG5307W1015엘브이엠씨홀딩스                        FS 000000000000NNN0NNN NNNNNNNNN0NNNNNNNN0000014100000100001NNN00NNN000000100N0900000004060520000000000002010113000000000019324000000000000004831010812       0 NNN00000286200000019900000011800107000003.5520250930000002724   NNN"),
                line("J0036221DKRA0036221D6KG모빌리티 122WR                        SW 000000000000NN 0NNN NN    N  0  N     0000008940000100000NNN00NNN000000100N09000000000479800000000000020231219000000000017892000000000000000000000         0 NNN00000000000000000000000000000000000000.00        000000159   NNN"),
                line("J0123401GKRA0123401G1뉴인텍 21R                              SR 000000000000NN 0NNN NN    N  0  N     0000000000000100000NNN00NNN000000100N09000000000000000000000000020260605000000000007392000000000000000000000         0 NNN00000000000000000000000000000000000000.00        000000000   NNN"),
                line("Q500061  KRG500000614신한 인버스 코스피 200 선물 ETN         EN 000000000000NN 0NNN3NN    N  0  N     0000026050000100001NNN00NNN000000100N09000000001514700000000000020211021000000000002000000000000000000000000         0 NNN00000000000000000000000000000000000000.00        000000052   NNN")
        );

        byte[] zipFile = zip(sampleLines.toArray(byte[][]::new));

        List<KisStockMasterRow> actualRows = parser.parseKospiMasterFile(zipFile);
        List<KisStockMasterRow> expectedRows = sampleLines.stream()
                .map(this::expectedRow)
                .map(ExpectedStockMasterRow::toActualShape)
                .toList();

        assertThat(actualRows).hasSameSizeAs(expectedRows);

        for (int i = 0; i < expectedRows.size(); i++) {
            KisStockMasterRow actual = actualRows.get(i);
            KisStockMasterRow expected = expectedRows.get(i);

            assertThat(actual.stockCode()).isEqualTo(expected.stockCode());
            assertThat(actual.standardCode()).isEqualTo(expected.standardCode());
            assertThat(actual.stockName()).isEqualTo(expected.stockName());
            assertThat(actual.market()).isEqualTo(expected.market());
            assertThat(actual.currencyCode()).isEqualTo(expected.currencyCode());
            assertThat(actual.status()).isEqualTo(expected.status());
            assertThat(actual.listedDate()).isEqualTo(expected.listedDate());
            assertThat(actual.capitalAmount()).isEqualTo(expected.capitalAmount());
            assertThat(actual.salesAmount()).isEqualTo(expected.salesAmount());
            assertThat(actual.netIncome()).isEqualTo(expected.netIncome());
            assertThat(actual.marketCap()).isEqualTo(expected.marketCap());
            assertThat(actual.previousVolume()).isEqualTo(expected.previousVolume());
        }
    }

    private byte[] zip(byte[]... lines) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("kospi_code.mst"));

            for (byte[] line : lines) {
                zipOutputStream.write(line);
                zipOutputStream.write('\n');
            }

            zipOutputStream.closeEntry();
        }

        return outputStream.toByteArray();
    }

    private byte[] line(String value) {
        return value.getBytes(KIS_FILE_CHARSET);
    }

    private ExpectedStockMasterRow expectedRow(byte[] line) {
        byte[] part1Bytes = Arrays.copyOfRange(line, 0, line.length - OFFICIAL_PART2_BYTE_LENGTH);
        byte[] part2Bytes = Arrays.copyOfRange(line, line.length - OFFICIAL_PART2_BYTE_LENGTH, line.length);
        String part1 = new String(part1Bytes, KIS_FILE_CHARSET);
        String part2 = new String(part2Bytes, StandardCharsets.US_ASCII);

        String trhtYn = officialField(part2, TRHT_YN_FIELD);
        String sltrYn = officialField(part2, SLTR_YN_FIELD);
        String mangIssuYn = officialField(part2, MANG_ISSU_YN_FIELD);

        return new ExpectedStockMasterRow(
                part1.substring(0, 9).trim(),
                part1.substring(9, 21).trim(),
                part1.substring(21).trim(),
                toStatus(trhtYn, sltrYn, mangIssuYn),
                parseDate(officialField(part2, STCK_LSTN_DATE_FIELD)),
                parseLong(officialField(part2, CPFN_FIELD)),
                parseLong(officialField(part2, SALE_ACCOUNT_FIELD)),
                parseLong(officialField(part2, THTR_NTIN_FIELD)),
                parseLong(officialField(part2, PRDY_AVLS_SCAL_FIELD)),
                parseLong(officialField(part2, PRDY_VOL_FIELD))
        );
    }

    private String officialField(String part2, int fieldIndex) {
        int start = 0;
        for (int i = 0; i < fieldIndex; i++) {
            start += OFFICIAL_PART2_FIELD_WIDTHS[i];
        }

        return part2.substring(start, start + OFFICIAL_PART2_FIELD_WIDTHS[fieldIndex]);
    }

    private StockStatus toStatus(String trhtYn, String sltrYn, String mangIssuYn) {
        if ("Y".equalsIgnoreCase(sltrYn.trim())) {
            return StockStatus.DELISTED;
        }

        if ("Y".equalsIgnoreCase(trhtYn.trim()) || "Y".equalsIgnoreCase(mangIssuYn.trim())) {
            return StockStatus.SUSPENDED;
        }

        return StockStatus.ACTIVE;
    }

    private LocalDate parseDate(String value) {
        String trimmed = value.trim();
        if (trimmed.isBlank() || "00000000".equals(trimmed)) {
            return null;
        }

        return LocalDate.parse(trimmed, DATE_FORMATTER);
    }

    private Long parseLong(String value) {
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        return Long.valueOf(trimmed);
    }

    private record ExpectedStockMasterRow(
            String stockCode,
            String standardCode,
            String stockName,
            StockStatus status,
            LocalDate listedDate,
            Long capitalAmount,
            Long salesAmount,
            Long netIncome,
            Long marketCap,
            Long previousVolume
    ) {

        private KisStockMasterRow toActualShape() {
            return new KisStockMasterRow(
                    stockCode,
                    standardCode,
                    stockName,
                    StockMarket.KOSPI,
                    CurrencyCode.KRW,
                    status,
                    listedDate,
                    capitalAmount,
                    salesAmount,
                    netIncome,
                    marketCap,
                    previousVolume
            );
        }
    }
}
