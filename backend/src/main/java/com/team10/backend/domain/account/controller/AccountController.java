package com.team10.backend.domain.account.controller;

import com.team10.backend.domain.account.dto.req.AccountCreateReq;
import com.team10.backend.domain.account.dto.req.AccountNicknameUpdateReq;
import com.team10.backend.domain.account.dto.res.AccountCreateRes;
import com.team10.backend.domain.account.dto.res.AccountDetailRes;
import com.team10.backend.domain.account.dto.res.AccountSummaryRes;
import com.team10.backend.domain.account.service.AccountService;
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
@RequestMapping("/api/v1/accounts")
@Tag(name = "Account", description = "계좌 API")
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "계좌 개설", description = "본인인증이 완료된 인증 사용자의 입출금 계좌를 개설합니다.")
    @PostMapping
    public ResponseEntity<AccountCreateRes> createAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AccountCreateReq request
    ) {
        AccountCreateRes response = accountService.createAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "계좌 별칭 수정", description = "인증 사용자가 본인 소유의 활성 계좌 별칭을 수정합니다.")
    @PatchMapping("/{accountId}/nickname")
    public ResponseEntity<AccountDetailRes> updateNickname(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long accountId,
            @Valid @RequestBody AccountNicknameUpdateReq request
    ){
        AccountDetailRes response = accountService.updateNickname(userId, accountId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "계좌 해지", description = "인증 사용자가 본인 소유의 활성 계좌를 해지합니다. 잔액이 0원인 계좌만 해지할 수 있습니다.")
    @PostMapping("/{accountId}/close")
    public ResponseEntity<AccountDetailRes> closeAccount(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long accountId
    ){
        AccountDetailRes response = accountService.closeAccount(userId, accountId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 계좌 목록 조회", description = "인증 사용자의 계좌 목록을 조회합니다. 해지된 계좌는 제외합니다.")
    @GetMapping
    public ResponseEntity<List<AccountSummaryRes>> getAccounts(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(accountService.getAccounts(userId));
    }

    @Operation(summary = "해지 계좌 목록 조회", description = "인증 사용자의 해지된 계좌 목록을 조회합니다.")
    @GetMapping("/closed")
    public ResponseEntity<List<AccountSummaryRes>> getClosedAccounts(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(accountService.getClosedAccounts(userId));
    }

    @Operation(summary = "내 계좌 상세 조회", description = "인증 사용자가 본인 소유 계좌의 상세 정보를 조회합니다.")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailRes> getAccount(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long accountId
    ) {
        return ResponseEntity.ok(accountService.getAccount(userId, accountId));
    }
}
