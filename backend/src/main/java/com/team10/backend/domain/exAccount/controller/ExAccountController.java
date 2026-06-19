package com.team10.backend.domain.exAccount.controller;

import com.team10.backend.domain.exAccount.dto.req.ExAccountCandidateReq;
import com.team10.backend.domain.exAccount.dto.req.ExAccountLinkReq;
import com.team10.backend.domain.exAccount.dto.req.ExAccountTransactionRefreshReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountCandidateRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRefreshRes;
import com.team10.backend.domain.exAccount.service.ExAccountService;
import com.team10.backend.domain.exAccount.service.ExAccountSyncService;
import com.team10.backend.domain.exAccount.service.ExAccountTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/external-accounts")
@Tag(name = "External Account", description = "외부 계좌 조회, 후보 확인, 연동, 거래내역 새로고침 API")
public class ExAccountController {
    private final ExAccountService exAccountService;
    private final ExAccountSyncService exAccountSyncService;
    private final ExAccountTransactionService exAccountTransactionService;

    @Operation(
            summary = "연동된 외부 계좌 목록 조회",
            description = "인증 사용자가 연동 버튼으로 저장한 외부 계좌 목록을 조회합니다. 외부기관 실시간 조회는 수행하지 않습니다."
    )
    @ApiResponse(responseCode = "200", description = "연동된 외부 계좌 목록 조회 성공")
    @GetMapping("/accounts")
    public ResponseEntity<List<ExAccountRes>> getAccounts(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(exAccountService.getAccounts(userId));
    }

    @Operation(
            summary = "외부 계좌 상세 및 거래내역 조회",
            description = "인증 사용자가 선택한 외부 계좌의 상세 정보와 해당 계좌 거래내역을 함께 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "외부 계좌 상세 및 거래내역 조회 성공"),
            @ApiResponse(responseCode = "404", description = "외부 계좌를 찾을 수 없음")
    })
    @GetMapping("/accounts/{exAccountId}")
    public ResponseEntity<ExAccountDetailRes> getAccountDetail(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "외부 계좌 ID", example = "10")
            @PathVariable Long exAccountId
    ) {
        return ResponseEntity.ok(exAccountService.getAccountDetail(userId, exAccountId));
    }

    @Operation(
            summary = "외부 계좌 거래내역 새로고침",
            description = "외부기관에서 새로 조회한 거래내역 목록을 요청 본문으로 받아 선택한 외부 계좌에 저장하거나 갱신합니다. 같은 계좌 안에서는 transactionKey 기준으로 중복 여부를 판단합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래내역 새로고침 성공"),
            @ApiResponse(responseCode = "400", description = "거래내역 요청값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "외부 계좌를 찾을 수 없음")
    })
    @PostMapping("/accounts/{exAccountId}/transactions/refresh")
    public ResponseEntity<ExAccountTransactionRefreshRes> refreshTransactions(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "거래내역을 새로고침할 외부 계좌 ID", example = "10")
            @PathVariable Long exAccountId,
            @Valid @RequestBody ExAccountTransactionRefreshReq request
    ) {
        return ResponseEntity.ok(exAccountTransactionService.refreshTransactions(userId, exAccountId, request.transactions()));
    }

    @Operation(
            summary = "외부 계좌 후보 조회",
            description = "외부기관에서 조회된 계좌 목록을 요청 본문으로 받아 DB에 저장하지 않고 연동 후보로 반환합니다. 이미 연동된 계좌는 linked=true로 표시합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "외부 계좌 후보 조회 성공"),
            @ApiResponse(responseCode = "400", description = "외부 계좌 후보 요청값 검증 실패")
    })
    @PostMapping("/candidates")
    public ResponseEntity<List<ExAccountCandidateRes>> getLinkCandidates(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ExAccountCandidateReq request
    ) {
        return ResponseEntity.ok(exAccountSyncService.getLinkCandidates(userId, request.accounts()));
    }

    @Operation(
            summary = "외부 계좌 연동",
            description = "사용자가 연동 버튼을 누른 단일 외부 계좌만 DB에 저장합니다. 같은 사용자, 기관, 계좌번호의 계좌가 이미 있으면 신규 저장하지 않고 최신 스냅샷만 갱신합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "외부 계좌 연동 성공"),
            @ApiResponse(responseCode = "400", description = "외부 계좌 연동 요청값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @PostMapping("/link")
    public ResponseEntity<ExAccountRes> linkAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ExAccountLinkReq request
    ) {
        ExAccountRes response = exAccountSyncService.linkAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
