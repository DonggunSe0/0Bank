package com.team10.backend.domain.investment.stock.controller;

import com.team10.backend.domain.investment.stock.dto.res.StockDetailRes;
import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.stock.service.StockQueryService;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockSortType;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/investment/stocks")
@Tag(name = "Investment Stock", description = "투자 주식 종목 조회 API")
public class StockController {

    private final StockQueryService stockQueryService;

    @Operation(summary = "주식 종목 검색", description = "2글자 이상 입력 키워드를 포함하는 KOSPI 종목명 기반 검색 결과를 조회합니다.")
    @GetMapping("/search")
    public ResponseEntity<List<StockSummaryRes>> searchStocks(
            @Parameter(description = "검색 키워드")
            @RequestParam(defaultValue = "") String keyword,

            @Parameter(description = "시장")
            @RequestParam(defaultValue = "KOSPI") StockMarket market
    ) {
        return ResponseEntity.ok(stockQueryService.search(keyword, market));
    }

    @Operation(summary = "주식 종목 목록 조회", description = "DB에 저장된 주식 종목을 시장, 상태, 정렬 기준으로 페이지 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<StockSummaryRes>> getStocks(
            @Parameter(description = "시장")
            @RequestParam(defaultValue = "KOSPI") StockMarket market,

            @Parameter(description = "종목 상태")
            @RequestParam(defaultValue = "ACTIVE") StockStatus status,

            @Parameter(description = "정렬 기준")
            @RequestParam(defaultValue = "STOCK_NAME") StockSortType sort,

            @Parameter(description = "정렬 방향")
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,

            @Parameter(description = "페이지 번호(0부터 시작)")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(stockQueryService.getStocks(market, status, sort, direction, page, size));
    }

    @Operation(summary = "주식 종목 상세 조회", description = "종목코드 기준으로 DB에 저장된 주식 종목 상세 정보를 조회합니다.")
    @GetMapping("/{stockCode}")
    public ResponseEntity<StockDetailRes> getStock(
            @Parameter(description = "종목코드")
            @PathVariable String stockCode
    ) {
        return ResponseEntity.ok(stockQueryService.getStock(stockCode));
    }
}
