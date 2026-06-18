package com.team10.backend.domain.transfer.controller;

import com.team10.backend.domain.transfer.dto.req.DepositReq;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "TransferController", description = "입금/송금 API")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/topUp")
    @Operation(summary = "입금")
    public ResponseEntity<TopUpRes> topUp(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositReq request) {
        TopUpRes response = transferService.topUp(userId, idempotencyKey, request.accountId(), request.amount(), request.memo());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "계좌 간 송금")
    public ResponseEntity<TransferRes> transfer(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferReq request) {
        TransferRes response = transferService.transfer(
                userId,
                idempotencyKey,
                request.senderAccountId(),
                request.receiverAccountNumber(),
                request.amount(),
                request.memo());
        return ResponseEntity.ok(response);
    }
}
