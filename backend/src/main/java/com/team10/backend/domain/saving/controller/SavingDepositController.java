package com.team10.backend.domain.saving.controller;

import com.team10.backend.domain.saving.dto.req.*;
import com.team10.backend.domain.saving.dto.res.*;
import com.team10.backend.domain.saving.service.SavingDepositService;
import com.team10.backend.domain.saving.type.DepositStatus;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import com.team10.backend.domain.saving.type.SavingProductType;
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
@Tag(name = "Saving Deposit", description = "예금/적금 API")
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

    @Operation(summary = "내 적금 목록 조회", description = "인증 사용자의 적금 목록을 조회합니다. 상태값으로 필터링할 수 있습니다.")
    @GetMapping("/installments")
    public ResponseEntity<List<InstallmentSummaryRes>> getInstallments(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) InstallmentStatus status
    ) {
        List<InstallmentSummaryRes> response =
                savingDepositService.getInstallments(userId, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 적금 상세 조회", description = "인증 사용자의 적금 상세 정보를 조회합니다.")
    @GetMapping("/installments/{installmentId}")
    public ResponseEntity<InstallmentDetailRes> getInstallment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long installmentId
    ){
        InstallmentDetailRes response = savingDepositService.getInstallment(userId, installmentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예상 이자 조회", description = "예금 또는 적금의 예상 이자와 만기 예상 수령액을 조회합니다.")
    @GetMapping("/{savingId}/interest-preview")
    public ResponseEntity<InterestPreviewRes> getInterestPreview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long savingId,
            @RequestParam SavingProductType savingType
    ) {
        InterestPreviewRes response =
                savingDepositService.getInterestPreview(userId, savingId,
                        savingType);

        return ResponseEntity.ok(response);
    }


    @Operation(summary = "중도 해지", description = "가입중 상태의 예금 또는 적금을 중도 해지합니다.")
    @PostMapping("/{savingId}/cancel")
    public ResponseEntity<EarlyCancelRes> cancelSaving(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long savingId,
            @Valid @RequestBody EarlyCancelReq request
    ) {
        EarlyCancelRes response = savingDepositService.cancelSaving(userId, savingId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "만기 처리", description = "만기일이 도래한 예금 또는 적금을 만기 처리합니다.")
    @PostMapping("/{savingId}/maturity")
    public ResponseEntity<MaturityRes> matureSaving(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long savingId,
            @Valid @RequestBody MaturityReq request
    ) {
        MaturityRes response =
                savingDepositService.matureSaving(userId, savingId, request);

        return ResponseEntity.ok(response);
    }
}
