package com.team10.backend.domain.investment.client.stock;

import static com.team10.backend.domain.investment.config.KisConstants.StockMaster.KOSPI_MASTER_FILE_NAME;

import com.team10.backend.domain.investment.client.stock.dto.KisStockMasterRow;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class KisStockMasterFileParser {

    private static final Charset KIS_FILE_CHARSET = Charset.forName("MS949");

    /**
     * 공식 Python 샘플의 row[-228:]은 줄 끝의 '\n'까지 포함한 문자열 기준이다.
     * 실제 Part 2 데이터 폭은 field_specs 합계와 struct의 char[n] 합계인 227바이트다.
     * 줄바꿈을 제거한 현재 파서에서 228바이트로 자르면 Part 1의 종목명 padding 공백 1바이트가
     * Part 2 앞에 섞여 모든 offset이 한 칸 밀린다.
     * Part 1에는 한글 종목명이 포함되므로 문자열로 먼저 자르지 않고 바이트 기준으로 분리한다.
     */
    private static final int PART2_BYTE_LENGTH = 227;

    /**
     * Part 1은 "단축코드 9바이트 + 표준코드 12바이트 + 한글종목명" 순서다.
     * Java substring의 end index는 exclusive이므로 0..9, 9..21 범위로 읽는다.
     */
    private static final int STOCK_CODE_END = 9;
    private static final int STANDARD_CODE_END = 21;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public List<KisStockMasterRow> parseKospiMasterFile(byte[] zipFile) {
        byte[] masterFile = extractMasterFile(zipFile);
        List<KisStockMasterRow> rows = splitLines(masterFile).stream()
                .filter(line -> line.length > 0)
                .map(this::parseLine)
                .toList();

        if (rows.isEmpty()) {
            throw new IllegalStateException("KIS stock master file is empty.");
        }

        return rows;
    }

    private byte[] extractMasterFile(byte[] zipFile) {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                /**
                 * zip 내부에 문서상 명시된 kospi_code.mst 파일이 있어야 정상 동기화 대상으로 본다.
                 */
                if (KOSPI_MASTER_FILE_NAME.equals(entry.getName())) {
                    return zipInputStream.readAllBytes();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read KIS stock master zip file.", e);
        }

        throw new IllegalStateException("KIS stock master file not found. fileName=" + KOSPI_MASTER_FILE_NAME);
    }

    private List<byte[]> splitLines(byte[] file) {
        List<byte[]> lines = new ArrayList<>();
        int start = 0;

        /**
         * 고정 길이 필드는 바이트 위치가 중요하므로 줄 단위 분리도 디코딩 전에 수행한다.
         */
        for (int i = 0; i < file.length; i++) {
            if (file[i] == '\n') {
                addLine(lines, file, start, i);
                start = i + 1;
            }
        }

        if (start < file.length) {
            addLine(lines, file, start, file.length);
        }

        return lines;
    }

    private void addLine(List<byte[]> lines, byte[] file, int start, int end) {
        int actualEnd = end;
        if (actualEnd > start && file[actualEnd - 1] == '\r') {
            actualEnd--;
        }

        if (actualEnd > start) {
            lines.add(Arrays.copyOfRange(file, start, actualEnd));
        }
    }

    private KisStockMasterRow parseLine(byte[] line) {
        if (line.length <= PART2_BYTE_LENGTH) {
            throw new IllegalArgumentException("Invalid KIS stock master line length. length=" + line.length);
        }

        /**
         * 전체 행의 끝에서 Part 2 길이만큼 잘라 고정 길이 영역으로 보고, 나머지를 Part 1로 본다.
         * 이 기준이 맞아야 아래 struct 누적 offset이 정확히 동작한다.
         */
        byte[] part1Bytes = Arrays.copyOfRange(line, 0, line.length - PART2_BYTE_LENGTH);
        byte[] part2Bytes = Arrays.copyOfRange(line, line.length - PART2_BYTE_LENGTH, line.length);

        String part1 = new String(part1Bytes, KIS_FILE_CHARSET);
        String part2 = new String(part2Bytes, StandardCharsets.US_ASCII);

        String stockCode = substring(part1, 0, STOCK_CODE_END).trim();
        String standardCode = substring(part1, STOCK_CODE_END, STANDARD_CODE_END).trim();
        String stockName = substring(part1, STANDARD_CODE_END, part1.length()).trim();

        /**
         * Part 2 인덱스는 KIS struct의 필드 길이를 앞에서부터 누적해서 계산한다.
         * 예: trht_yn은 앞선 필드 길이 합계가 60바이트라서 60..61 범위다.
         */
        String trhtYn = substring(part2, 60, 61);
        String sltrYn = substring(part2, 61, 62);
        String mangIssuYn = substring(part2, 62, 63);

        return new KisStockMasterRow(
                stockCode,
                standardCode,
                stockName,
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                toStatus(trhtYn, sltrYn, mangIssuYn),
                parseDate(substring(part2, 105, 113)),
                parseLong("cpfn", substring(part2, 128, 149)),
                parseLong("sale_account", substring(part2, 163, 172)),
                parseLong("thtr_ntin", substring(part2, 190, 195)),
                parseLong("prdy_avls_scal", substring(part2, 212, 221)),
                parseLong("prdy_vol", substring(part2, 81, 93))
        );
    }

    private StockStatus toStatus(String trhtYn, String sltrYn, String mangIssuYn) {
        if (isYes(sltrYn)) {
            return StockStatus.DELISTED;
        }

        if (isYes(trhtYn) || isYes(mangIssuYn)) {
            return StockStatus.SUSPENDED;
        }

        return StockStatus.ACTIVE;
    }

    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(value.trim());
    }

    private LocalDate parseDate(String value) {
        String trimmed = value.trim();
        if (trimmed.isBlank() || "00000000".equals(trimmed)) {
            return null;
        }

        return LocalDate.parse(trimmed, DATE_FORMATTER);
    }

    private Long parseLong(String fieldName, String value) {
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid KIS stock master number. field=" + fieldName + ", value=" + value,
                    e
            );
        }
    }

    private String substring(String value, int start, int end) {
        if (value.length() < end) {
            throw new IllegalArgumentException(
                    "Invalid KIS stock master field length. length=" + value.length()
                            + ", start=" + start
                            + ", end=" + end
            );
        }

        return value.substring(start, end);
    }
}
