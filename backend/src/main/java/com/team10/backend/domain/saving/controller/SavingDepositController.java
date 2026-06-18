package com.team10.backend.domain.saving.controller;

import com.team10.backend.domain.saving.dto.req.DepositCreateReq;
import com.team10.backend.domain.saving.dto.req.InstallmentCreateReq;
import com.team10.backend.domain.saving.dto.res.DepositCreateRes;
import com.team10.backend.domain.saving.dto.res.DepositDetailRes;
import com.team10.backend.domain.saving.dto.res.DepositSummaryRes;
import com.team10.backend.domain.saving.dto.res.InstallmentCreateRes;
import com.team10.backend.domain.saving.service.SavingDepositService;
import com.team10.backend.domain.saving.type.DepositStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/savings")
@Tag(name = "Saving Deposit", description = "예금 가입 API")
public class SavingDepositController {

    private final SavingDepositService savingDepositService;

    @Operation(summary = "예금 가입", description = "인증 사용자가 활성 예금 상품에 가입합니다.")
    @PostMapping("/deposits")
    public ResponseEntity<DepositCreateRes> createDeposit(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DepositCreateReq request
    ) {
        DepositCreateRes response =
                savingDepositService.createDeposit(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "내 예금 목록 조회", description = "인증 사용자의 예금 목록을 조회합니다. 상태값으로 필터링할 수 있습니다.")
    @GetMapping("/deposits")
    public ResponseEntity<List<DepositSummaryRes>> getDeposits(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) DepositStatus status
    ) {
        List<DepositSummaryRes> response =
                savingDepositService.getDeposits(userId, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 예금 상세 조회", description = "인증 사용자의 예금 상세 정보를 조회합니다.")
    @GetMapping("/deposits/{depositId}")
    public ResponseEntity<DepositDetailRes> getDeposit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long depositId
    ) {
        DepositDetailRes response =
                savingDepositService.getDeposit(userId, depositId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "적금 가입", description = "인증 사용자가 활성 적금 상품에 가입합니다.")
    @PostMapping("/installments")
    public ResponseEntity<InstallmentCreateRes> createInstallment(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InstallmentCreateReq request
    ) {
        InstallmentCreateRes response =
                savingDepositService.createInstallment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
