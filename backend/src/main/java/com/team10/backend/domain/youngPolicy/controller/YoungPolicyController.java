package com.team10.backend.domain.youngPolicy.controller;

import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyReq;
import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyRecommendReq;
import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicySearchReq;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyDetailRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyRecommendRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySummaryRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySyncRes;
import com.team10.backend.domain.youngPolicy.service.PolicyRagRecommendService;
import com.team10.backend.domain.youngPolicy.service.YoungPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/youth-policies")
@Tag(name = "YoungPolicy", description = "청년정책 API")
public class YoungPolicyController {

    private final YoungPolicyService youngPolicyService;
    private final PolicyRagRecommendService policyRagRecommendService;

    // DB에 저장된 청년 정책 목록 조회
    @GetMapping
    @Operation(summary = "청년정책 목록 조회", description = "DB에 저장된 청년정책 목록을 조회합니다.")
    public ResponseEntity<List<YoungPolicySummaryRes>> getPolicies() {
        return ResponseEntity.ok(youngPolicyService.getPolicies());
    }

    // DB에 저장된 청년 정책 필터링 검색
    @GetMapping("/search")
    @Operation(summary = "청년정책 필터링 검색", description = "나이, 지역, 카테고리, 키워드로 청년정책을 필터링하여 검색합니다.")
    public ResponseEntity<Page<YoungPolicySummaryRes>> searchPolicies(
            YoungPolicySearchReq filter,
            Pageable pageable
    ) {
        return ResponseEntity.ok(youngPolicyService.searchPolicies(filter, pageable));
    }

    // DB에 저장된 청년 정책 상세 조회
    @GetMapping("/{id}")
    @Operation(summary = "청년정책 상세 조회", description = "DB에 저장된 청년정책을 ID로 조회합니다.")
    public ResponseEntity<YoungPolicyDetailRes> getPolicy(
            @Parameter(description = "청년정책 ID", example = "1")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(youngPolicyService.getPolicy(id));
    }

    // 외부 청년 정책 API를 호출하여 DB에 저장/갱신
    @PostMapping("/sync")
    @Operation(
            summary = "청년정책 동기화",
            description = "청년센터 공식 OpenAPI를 호출해 청년정책을 DB에 저장하거나 갱신합니다."
    )
    public ResponseEntity<YoungPolicySyncRes> syncPolicies(@Valid @RequestBody YoungPolicyReq request) {
        return ResponseEntity.ok(youngPolicyService.syncPolicies(request));
    }

    // 모든 외부 청년 정책을 페이지별로 가져와서 DB에 저장/갱신
    @PostMapping("/sync-all")
    @Operation(
            summary = "전체 청년정책 동기화",
            description = "청년센터 공식 OpenAPI의 모든 페이지를 순회하며 전체 청년정책을 DB에 저장하거나 갱신합니다."
    )
    public ResponseEntity<YoungPolicySyncRes> syncAllPolicies() {
        return ResponseEntity.ok(youngPolicyService.syncAllPolicies());
    }

    // RAG 기반 정책 추천
    @Operation(
            summary = "RAG 기반 맞춤 청년정책 추천",
            description = "사용자의 정보(나이, 지역, 질문 등)를 입력받아 AI(LLM) RAG 탐색을 통해 알맞은 정책 4가지와 맞춤 사유를 생성하여 추천합니다."
    )
    @PostMapping("/recommend")
    public ResponseEntity<YoungPolicyRecommendRes> recommendPolicies(
            @Valid @RequestBody YoungPolicyRecommendReq request
    ) {
        return ResponseEntity.ok(policyRagRecommendService.recommend(request));
    }
}
