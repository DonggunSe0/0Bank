package com.team10.backend.domain.investment.realtime.controller;

import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSseConnection;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/investment/realtime/orderbooks")
@Tag(name = "Realtime Orderbook", description = "실시간 주식 호가 API")
public class RealtimeOrderbookController {

    private final RealtimeOrderbookStreamService realtimeOrderbookStreamService;

    @Operation(summary = "실시간 호가 스트림 생성 및 종목 구독", description = "SSE 스트림을 생성하고 지정 종목 실시간 호가 구독을 시작합니다.")
    @GetMapping(value = "/{stockCode}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> openStream(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @PathVariable String stockCode
    ) {
        RealtimeOrderbookSseConnection connection = realtimeOrderbookStreamService.openStream(userId, stockCode);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(connection.emitter());
    }

    @Operation(summary = "실시간 호가 스트림 종료 및 종목 구독 해지", description = "SSE 스트림을 종료하고 해당 스트림의 종목 구독을 해지합니다.")
    @DeleteMapping("/streams/{streamId}")
    public ResponseEntity<Void> closeStream(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @PathVariable String streamId
    ) {
        realtimeOrderbookStreamService.closeStream(userId, streamId);
        return ResponseEntity.noContent().build();
    }
}
