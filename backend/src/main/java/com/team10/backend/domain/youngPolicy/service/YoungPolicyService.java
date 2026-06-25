package com.team10.backend.domain.youngPolicy.service;

import com.team10.backend.domain.youngPolicy.client.YoungPolicyClient;
import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyReq;
import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicySearchReq;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyDetailRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyExternalRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySummaryRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySyncRes;
import com.team10.backend.domain.youngPolicy.exception.YoungPolicyErrorCode;
import com.team10.backend.domain.youngPolicy.repository.YoungPolicyRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class YoungPolicyService {

    private final YoungPolicyRepository youngPolicyRepository;
    private final YoungPolicyClient youngPolicyClient;

    // 저장된 청년정책 목록을 요약 응답으로 조회합니다.
    public List<YoungPolicySummaryRes> getPolicies() {
        return youngPolicyRepository.findAll().stream()
                .map(YoungPolicySummaryRes::from)
                .toList();
    }

    // 필터 조건을 받아서 동적 쿼리로 정책을 검색하고 페이징 처리합니다.
    public Page<YoungPolicySummaryRes> searchPolicies(YoungPolicySearchReq filter, Pageable pageable) {
        return youngPolicyRepository.search(filter, pageable)
                .map(YoungPolicySummaryRes::from);
    }

    // 저장된 청년정책 한 건을 상세 응답으로 조회합니다.
    public YoungPolicyDetailRes getPolicy(Long id) {
        return youngPolicyRepository.findById(id)
                .map(YoungPolicyDetailRes::from)
                .orElseThrow(() -> new BusinessException(YoungPolicyErrorCode.YOUNG_POLICY_NOT_FOUND));
    }

    // 외부 정책을 정책번호 기준으로 새로 저장하거나 갱신합니다.
    @Transactional
    public YoungPolicySyncRes syncPolicies(YoungPolicyReq request) {
        YoungPolicyExternalRes response = fetchPolicies(request);
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        List<YoungPolicyExternalRes.PolicyItem> policyItems = response.policyItems();

        for (YoungPolicyExternalRes.PolicyItem item : policyItems) {
            // 정책번호가 없으면 중복 확인이 불가능해 저장하지 않습니다.
            if (!item.hasPolicyId()) {
                skippedCount++;
                continue;
            }

            var policy = youngPolicyRepository.findByPolicyId(item.plcyNo());
            if (policy.isPresent()) {
                item.update(policy.get());
                updatedCount++;
            } else {
                youngPolicyRepository.save(item.toEntity());
                createdCount++;
            }
        }

        return new YoungPolicySyncRes(
                policyItems.size(),
                createdCount,
                updatedCount,
                skippedCount
        );
    }

    // 외부 API의 모든 정책을 페이지를 넘겨가며 전체 동기화합니다.
    @Transactional
    public YoungPolicySyncRes syncAllPolicies() {
        int pageNum = 1;
        int pageSize = 100;
        int totalCreated = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;
        int totalFetched = 0;

        while (true) {
            YoungPolicyReq request = new YoungPolicyReq(pageNum, pageSize);
            YoungPolicyExternalRes response = fetchPolicies(request);
            List<YoungPolicyExternalRes.PolicyItem> policyItems = response.policyItems();

            if (policyItems == null || policyItems.isEmpty()) {
                break;
            }

            int createdCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            for (YoungPolicyExternalRes.PolicyItem item : policyItems) {
                if (!item.hasPolicyId()) {
                    skippedCount++;
                    continue;
                }

                var policy = youngPolicyRepository.findByPolicyId(item.plcyNo());
                if (policy.isPresent()) {
                    item.update(policy.get());
                    updatedCount++;
                } else {
                    youngPolicyRepository.save(item.toEntity());
                    createdCount++;
                }
            }

            totalCreated += createdCount;
            totalUpdated += updatedCount;
            totalSkipped += skippedCount;
            totalFetched += policyItems.size();

            if (policyItems.size() < pageSize) {
                break;
            }

            pageNum++;

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return new YoungPolicySyncRes(
                totalFetched,
                totalCreated,
                totalUpdated,
                totalSkipped
        );
    }

    // 외부 API 오류를 청년정책 도메인 예외로 바꿉니다.
    private YoungPolicyExternalRes fetchPolicies(YoungPolicyReq request) {
        try {
            YoungPolicyExternalRes response = youngPolicyClient.fetchPolicies(request);
            return response == null ? new YoungPolicyExternalRes(new YoungPolicyExternalRes.Result(List.of())) : response;
        } catch (RuntimeException e) {
            throw new BusinessException(YoungPolicyErrorCode.YOUNG_POLICY_SYNC_FAILED, e);
        }
    }

}
