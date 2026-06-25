package com.team10.backend.domain.investment.watchlist.controller;

import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.watchlist.service.StockWatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/investment/watchlists")
@Tag(name = "Investment Watchlist", description = "투자 관심 종목 API")
public class StockWatchlistController {

    private final StockWatchlistService stockWatchlistService;

    @Operation(summary = "관심 종목 등록", description = "인증 사용자의 관심 종목에 특정 종목을 등록합니다.")
    @PostMapping("/{stockId}")
    public ResponseEntity<StockSummaryRes> addWatchlist(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @Parameter(description = "종목 ID")
            @PathVariable Long stockId
    ) {
        StockSummaryRes response = stockWatchlistService.addWatchlist(userId, stockId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "내 관심 종목 목록 조회", description = "인증 사용자의 관심 종목 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<StockSummaryRes>> getWatchlists(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(stockWatchlistService.getWatchlists(userId));
    }

    @Operation(summary = "관심 종목 삭제", description = "인증 사용자의 관심 종목에서 특정 종목을 제거합니다.")
    @DeleteMapping("/{stockId}")
    public ResponseEntity<Void> removeWatchlist(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @Parameter(description = "종목 ID")
            @PathVariable Long stockId
    ) {
        stockWatchlistService.removeWatchlist(userId, stockId);
        return ResponseEntity.noContent().build();
    }
}
