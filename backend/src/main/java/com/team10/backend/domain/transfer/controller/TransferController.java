package com.team10.backend.domain.transfer.controller;

import com.team10.backend.domain.transfer.dto.req.DepositReq;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.dto.res.DepositRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "TransferController", description = "입금/송금 API")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/deposit")
    @Operation(summary = "입금")
    public ResponseEntity<DepositRes> deposit(@Valid @RequestBody DepositReq request) {
        DepositRes response = transferService.deposit(request.accountId(), request.amount(), request.memo());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "계좌 간 송금")
    public ResponseEntity<TransferRes> transfer(@Valid @RequestBody TransferReq request) {
        TransferRes response = transferService.transfer(
                request.senderAccountId(),
                request.receiverAccountNumber(),
                request.amount(),
                request.memo());
        return ResponseEntity.ok(response);
    }
}
