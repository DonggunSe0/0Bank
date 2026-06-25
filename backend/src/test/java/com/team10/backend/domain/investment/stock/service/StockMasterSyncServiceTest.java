package com.team10.backend.domain.investment.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.client.stock.KisStockMasterClient;
import com.team10.backend.domain.investment.client.stock.KisStockMasterFileParser;
import com.team10.backend.domain.investment.client.stock.dto.KisStockMasterRow;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.repository.StockRepository;
import com.team10.backend.domain.investment.stock.service.StockMasterSyncService.StockMasterSyncResult;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class StockMasterSyncServiceTest {

    @Mock
    private KisStockMasterClient stockMasterClient;

    @Mock
    private KisStockMasterFileParser stockMasterFileParser;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private StockMasterSyncService stockMasterSyncService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    @Test
    @DisplayName("KOSPI 종목 마스터를 다운로드하고 DB에 최신화한다")
    void syncKospiMaster() {
        byte[] zipFile = new byte[]{1, 2, 3};
        KisStockMasterRow samsung = row(
                "005930",
                "KR7005930003",
                "삼성전자",
                StockStatus.ACTIVE,
                1_000L
        );
        KisStockMasterRow hynix = row(
                "000660",
                "KR7000660001",
                "SK하이닉스",
                StockStatus.SUSPENDED,
                2_000L
        );
        Stock existingStock = Stock.create(
                "005930",
                "OLD_STANDARD_CODE",
                "구 삼성전자",
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                StockStatus.SUSPENDED,
                LocalDate.of(1970, 1, 1),
                0L,
                0L,
                0L,
                0L,
                0L
        );

        when(stockMasterClient.downloadKospiMasterFile()).thenReturn(zipFile);
        when(stockMasterFileParser.parseKospiMasterFile(zipFile)).thenReturn(List.of(samsung, hynix));
        when(stockRepository.findAllByStockCodeIn(anyCollection())).thenReturn(List.of(existingStock));
        when(stockRepository.touchSyncedStocks(anyCollection(), any(LocalDateTime.class))).thenReturn(2);
        when(stockRepository.markStocksNotSyncedAsDelisted(
                eq(StockMarket.KOSPI),
                any(LocalDateTime.class),
                eq(StockStatus.DELISTED)
        )).thenReturn(1);

        StockMasterSyncResult result = stockMasterSyncService.syncKospiMaster();

        assertThat(result.downloadedRows()).isEqualTo(2);
        assertThat(result.uniqueRows()).isEqualTo(2);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.touched()).isEqualTo(2);
        assertThat(result.delisted()).isEqualTo(1);

        assertThat(existingStock.getStandardCode()).isEqualTo("KR7005930003");
        assertThat(existingStock.getStockName()).isEqualTo("삼성전자");
        assertThat(existingStock.getStatus()).isEqualTo(StockStatus.ACTIVE);
        assertThat(existingStock.getMarketCap()).isEqualTo(1_000L);

        verify(stockRepository).save(anyStockWithCode("000660"));

        InOrder inOrder = inOrder(stockRepository);
        inOrder.verify(stockRepository).findAllByStockCodeIn(anyCollection());
        inOrder.verify(stockRepository).save(any(Stock.class));
        inOrder.verify(stockRepository).flush();
        inOrder.verify(stockRepository).touchSyncedStocks(anyCollection(), any(LocalDateTime.class));
        inOrder.verify(stockRepository).markStocksNotSyncedAsDelisted(
                eq(StockMarket.KOSPI),
                any(LocalDateTime.class),
                eq(StockStatus.DELISTED)
        );
    }

    @Test
    @DisplayName("다운로드 실패 시 DB 작업을 수행하지 않고 예외를 전파한다")
    void failToDownload() {
        RuntimeException exception = new RuntimeException("download failed");
        when(stockMasterClient.downloadKospiMasterFile()).thenThrow(exception);

        assertThatThrownBy(() -> stockMasterSyncService.syncKospiMaster())
                .isSameAs(exception);

        verifyNoInteractions(stockMasterFileParser, stockRepository);
    }

    @Test
    @DisplayName("파싱 실패 시 DB 작업을 수행하지 않고 예외를 전파한다")
    void failToParse() {
        byte[] zipFile = new byte[]{1, 2, 3};
        RuntimeException exception = new RuntimeException("parse failed");
        when(stockMasterClient.downloadKospiMasterFile()).thenReturn(zipFile);
        when(stockMasterFileParser.parseKospiMasterFile(zipFile)).thenThrow(exception);

        assertThatThrownBy(() -> stockMasterSyncService.syncKospiMaster())
                .isSameAs(exception);

        verifyNoInteractions(stockRepository);
        verify(transactionTemplate, never()).execute(any(TransactionCallback.class));
    }

    @Test
    @DisplayName("동일한 종목 코드가 중복되면 마지막 row를 기준으로 저장한다")
    void syncKospiMasterUsesLastRowWhenStockCodeIsDuplicated() {
        byte[] zipFile = new byte[]{1, 2, 3};
        KisStockMasterRow oldSamsung = row(
                "005930",
                "OLD_STANDARD_CODE",
                "구 삼성전자",
                StockStatus.SUSPENDED,
                1_000L
        );
        KisStockMasterRow latestSamsung = row(
                "005930",
                "KR7005930003",
                "삼성전자",
                StockStatus.ACTIVE,
                2_000L
        );

        when(stockMasterClient.downloadKospiMasterFile()).thenReturn(zipFile);
        when(stockMasterFileParser.parseKospiMasterFile(zipFile)).thenReturn(List.of(oldSamsung, latestSamsung));
        when(stockRepository.findAllByStockCodeIn(anyCollection())).thenReturn(List.of());
        when(stockRepository.touchSyncedStocks(anyCollection(), any(LocalDateTime.class))).thenReturn(1);
        when(stockRepository.markStocksNotSyncedAsDelisted(
                eq(StockMarket.KOSPI),
                any(LocalDateTime.class),
                eq(StockStatus.DELISTED)
        )).thenReturn(0);

        StockMasterSyncResult result = stockMasterSyncService.syncKospiMaster();

        assertThat(result.downloadedRows()).isEqualTo(2);
        assertThat(result.uniqueRows()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.touched()).isEqualTo(1);
        assertThat(result.delisted()).isZero();

        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stockCaptor.capture());
        Stock savedStock = stockCaptor.getValue();
        assertThat(savedStock.getStockCode()).isEqualTo("005930");
        assertThat(savedStock.getStandardCode()).isEqualTo("KR7005930003");
        assertThat(savedStock.getStockName()).isEqualTo("삼성전자");
        assertThat(savedStock.getStatus()).isEqualTo(StockStatus.ACTIVE);
        assertThat(savedStock.getMarketCap()).isEqualTo(2_000L);
    }

    @Test
    @DisplayName("동기화된 종목 updatedAt 갱신은 500개 단위로 나누어 수행한다")
    void syncKospiMasterTouchesSyncedStocksByChunk() {
        byte[] zipFile = new byte[]{1, 2, 3};
        List<KisStockMasterRow> rows = IntStream.rangeClosed(1, 501)
                .mapToObj(i -> row(
                        String.format("%06d", i),
                        "KR" + String.format("%010d", i),
                        "종목" + i,
                        StockStatus.ACTIVE,
                        (long) i
                ))
                .toList();

        when(stockMasterClient.downloadKospiMasterFile()).thenReturn(zipFile);
        when(stockMasterFileParser.parseKospiMasterFile(zipFile)).thenReturn(rows);
        when(stockRepository.findAllByStockCodeIn(anyCollection())).thenReturn(List.of());
        when(stockRepository.touchSyncedStocks(anyCollection(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> invocation.<Collection<String>>getArgument(0).size());
        when(stockRepository.markStocksNotSyncedAsDelisted(
                eq(StockMarket.KOSPI),
                any(LocalDateTime.class),
                eq(StockStatus.DELISTED)
        )).thenReturn(0);

        StockMasterSyncResult result = stockMasterSyncService.syncKospiMaster();

        assertThat(result.downloadedRows()).isEqualTo(501);
        assertThat(result.uniqueRows()).isEqualTo(501);
        assertThat(result.inserted()).isEqualTo(501);
        assertThat(result.touched()).isEqualTo(501);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> stockCodesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(stockRepository, times(2)).touchSyncedStocks(stockCodesCaptor.capture(), any(LocalDateTime.class));
        assertThat(stockCodesCaptor.getAllValues())
                .extracting(Collection::size)
                .containsExactly(500, 1);
    }

    private KisStockMasterRow row(
            String stockCode,
            String standardCode,
            String stockName,
            StockStatus status,
            Long marketCap
    ) {
        return new KisStockMasterRow(
                stockCode,
                standardCode,
                stockName,
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                status,
                LocalDate.of(1975, 6, 11),
                100L,
                200L,
                300L,
                marketCap,
                500L
        );
    }

    private Stock anyStockWithCode(String stockCode) {
        return org.mockito.ArgumentMatchers.argThat((ArgumentMatcher<Stock>)
                stock -> stock != null && stockCode.equals(stock.getStockCode()));
    }
}
