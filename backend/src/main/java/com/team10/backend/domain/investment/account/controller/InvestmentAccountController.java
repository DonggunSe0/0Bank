package com.team10.backend.domain.investment.account.controller;

import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCloseReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCreateReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountUpdateReq;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCloseRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCreateRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountDetailRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountOpenVerificationRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountSummaryRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountUpdateRes;
import com.team10.backend.domain.investment.account.service.InvestmentAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/investment/accounts")
@Tag(name = "Investment Account", description = "투자 계좌 API")
public class InvestmentAccountController {

    private final InvestmentAccountService investmentAccountService;

    @Operation(summary = "내 투자 계좌 목록 조회", description = "인증 사용자의 해지되지 않은 투자 계좌 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<InvestmentAccountSummaryRes>> getAccounts(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(investmentAccountService.getAccounts(userId));
    }

    @Operation(summary = "내 투자 계좌 상세 조회", description = "인증 사용자가 본인 소유의 해지되지 않은 투자 계좌 상세 정보를 조회합니다.")
    @GetMapping("/{accountId}")
    public ResponseEntity<InvestmentAccountDetailRes> getAccount(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @PathVariable Long accountId
    ) {
        return ResponseEntity.ok(investmentAccountService.getAccount(userId, accountId));
    }

    @Operation(summary = "투자 계좌 개설 인증키 발급", description = "본인인증이 완료된 인증 사용자에게 투자 계좌 개설 인증키를 발급합니다.")
    @PostMapping("/open-verification")
    public ResponseEntity<InvestmentAccountOpenVerificationRes> issueOpenVerificationKey(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(investmentAccountService.issueOpenVerificationKey(userId));
    }

    @Operation(summary = "투자 계좌 개설", description = "본인인증과 개설 인증키 검증을 완료한 인증 사용자의 투자 계좌를 개설합니다.")
    @PostMapping
    public ResponseEntity<InvestmentAccountCreateRes> createAccount(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @Valid @RequestBody InvestmentAccountCreateReq request
    ) {
        InvestmentAccountCreateRes response = investmentAccountService.createAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "투자 계좌 정보 수정", description = "계좌 비밀번호 검증 후 전달된 별칭 또는 새 비밀번호만 수정합니다.")
    @PatchMapping("/{accountId}")
    public ResponseEntity<InvestmentAccountUpdateRes> updateAccount(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @PathVariable Long accountId,

            @Valid @RequestBody InvestmentAccountUpdateReq request
    ) {
        InvestmentAccountUpdateRes response = investmentAccountService.updateAccount(userId, accountId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "투자 계좌 해지", description = "계좌 비밀번호 검증 후 예수금, 보유 종목, 미체결 주문이 없는 투자 계좌를 CLOSED 상태로 변경합니다.")
    @PostMapping("/{accountId}/close")
    public ResponseEntity<InvestmentAccountCloseRes> closeAccount(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @PathVariable Long accountId,

            @Valid @RequestBody InvestmentAccountCloseReq request
    ) {
        InvestmentAccountCloseRes response = investmentAccountService.closeAccount(userId, accountId, request);
        return ResponseEntity.ok(response);
    }
}
