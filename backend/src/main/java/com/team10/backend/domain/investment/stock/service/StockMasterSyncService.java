package com.team10.backend.domain.investment.stock.service;

import com.team10.backend.domain.investment.client.stock.KisStockMasterClient;
import com.team10.backend.domain.investment.client.stock.KisStockMasterFileParser;
import com.team10.backend.domain.investment.client.stock.dto.KisStockMasterRow;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.repository.StockRepository;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMasterSyncService {

    private static final int TOUCH_CHUNK_SIZE = 500;

    private final KisStockMasterClient stockMasterClient;
    private final KisStockMasterFileParser stockMasterFileParser;
    private final StockRepository stockRepository;
    private final TransactionTemplate transactionTemplate;

    // TODO : 추후 코스닥, 미국 주식 등 다른 시장 또는 다른 거래소가 도입될 경우 커맨드 패턴 & 전략 패턴의 도입을 고려한다
    public StockMasterSyncResult syncKospiMaster() {
        LocalDateTime syncStartedAt = LocalDateTime.now();
        log.info("KOSPI stock master sync started. syncStartedAt={}", syncStartedAt);

        try {
            /**
             * 외부 다운로드와 파일 파싱은 DB 트랜잭션 밖에서 먼저 끝낸다.
             * 실패한 파일을 기준으로 기존 종목을 DELISTED 처리하지 않기 위해서다.
             */
            byte[] zipFile = stockMasterClient.downloadKospiMasterFile();
            List<KisStockMasterRow> rows = stockMasterFileParser.parseKospiMasterFile(zipFile);

            StockMasterSyncResult result = transactionTemplate.execute(status ->
                    synchronizeRows(rows, syncStartedAt)
            );

            log.info(
                    "KOSPI stock master sync completed. syncStartedAt={}, syncFinishedAt={}, downloadedRows={}, uniqueRows={}, inserted={}, updated={}, touched={}, delisted={}",
                    syncStartedAt,
                    LocalDateTime.now(),
                    result.downloadedRows(),
                    result.uniqueRows(),
                    result.inserted(),
                    result.updated(),
                    result.touched(),
                    result.delisted()
            );

            return result;
        } catch (RuntimeException e) {
            log.error("KOSPI stock master sync failed. syncStartedAt={}", syncStartedAt, e);
            throw e;
        }
    }

    private StockMasterSyncResult synchronizeRows(List<KisStockMasterRow> rows, LocalDateTime syncStartedAt) {
        /**
         * 같은 stockCode가 중복으로 들어오면 뒤의 값을 사용한다.
         * KIS 파일 기준으로는 중복이 없어야 하지만, Map으로 만들어 이후 조회와 Upsert 기준을 단일화한다.
         */
        Map<String, KisStockMasterRow> rowsByStockCode = rows.stream()
                .collect(Collectors.toMap(
                        KisStockMasterRow::stockCode,
                        Function.identity(),
                        (previous, current) -> current,
                        LinkedHashMap::new
                ));

        if (rowsByStockCode.isEmpty()) {
            throw new IllegalStateException("KIS stock master rows are empty.");
        }

        List<String> stockCodes = new ArrayList<>(rowsByStockCode.keySet());

        /**
         * stockCode를 기준으로 기존 종목을 한 번에 조회해 N+1 조회를 피한다.
         * TODO: 추후 거래소, 시장 확대 시 IN절 파라미터 수 제어를 위해 CHUNK 단위로 쿼리 분할 고려
         */
        Map<String, Stock> existingStocks = stockRepository.findAllByStockCodeIn(stockCodes).stream()
                .collect(Collectors.toMap(Stock::getStockCode, Function.identity()));

        int inserted = 0;
        int updated = 0;

        for (KisStockMasterRow row : rowsByStockCode.values()) {
            Stock stock = existingStocks.get(row.stockCode());
            if (stock == null) {
                stockRepository.save(toEntity(row));
                inserted++;
                continue;
            }

            stock.updateMaster(
                    row.standardCode(),
                    row.stockName(),
                    row.market(),
                    row.currencyCode(),
                    row.status(),
                    row.listedDate(),
                    row.capitalAmount(),
                    row.salesAmount(),
                    row.netIncome(),
                    row.marketCap(),
                    row.previousVolume()
            );
            updated++;
        }

        /**
         * Upsert 결과를 먼저 DB에 반영한 뒤, 이번 파일에 존재한 모든 종목의 updatedAt을 syncStartedAt으로 맞춘다.
         * 값이 기존과 완전히 같으면 JPA dirty checking이 발생하지 않아 updatedAt이 그대로일 수 있기 때문이다.
         */
        stockRepository.flush();
        int touched = touchSyncedStocks(stockCodes, syncStartedAt);

        /**
         * touch되지 않은 KOSPI 종목은 최신 파일에 없던 종목이므로 DELISTED로 처리한다.
         */
        int delisted = stockRepository.markStocksNotSyncedAsDelisted(
                StockMarket.KOSPI,
                syncStartedAt,
                StockStatus.DELISTED
        );

        return new StockMasterSyncResult(
                rows.size(),
                rowsByStockCode.size(),
                inserted,
                updated,
                touched,
                delisted
        );
    }

    private int touchSyncedStocks(List<String> stockCodes, LocalDateTime syncStartedAt) {
        int touched = 0;

        /**
         * IN 절 크기가 과도하게 커지는 것을 피하기 위해 일정 크기로 나눠 updatedAt을 갱신한다.
         */
        for (int start = 0; start < stockCodes.size(); start += TOUCH_CHUNK_SIZE) {
            int end = Math.min(start + TOUCH_CHUNK_SIZE, stockCodes.size());
            touched += stockRepository.touchSyncedStocks(stockCodes.subList(start, end), syncStartedAt);
        }

        return touched;
    }

    private Stock toEntity(KisStockMasterRow row) {
        return Stock.create(
                row.stockCode(),
                row.standardCode(),
                row.stockName(),
                row.market(),
                row.currencyCode(),
                row.status(),
                row.listedDate(),
                row.capitalAmount(),
                row.salesAmount(),
                row.netIncome(),
                row.marketCap(),
                row.previousVolume()
        );
    }

    public record StockMasterSyncResult(
            int downloadedRows,
            int uniqueRows,
            int inserted,
            int updated,
            int touched,
            int delisted
    ) {
    }
}
