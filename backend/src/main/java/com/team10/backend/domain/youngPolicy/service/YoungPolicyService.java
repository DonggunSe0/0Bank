package com.team10.backend.domain.youngPolicy.service;

import com.team10.backend.domain.youngPolicy.client.YoungPolicyClient;
import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicyReq;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyDetailRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicyExternalRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySummaryRes;
import com.team10.backend.domain.youngPolicy.dto.res.YoungPolicySyncRes;
import com.team10.backend.domain.youngPolicy.exception.YoungPolicyErrorCode;
import com.team10.backend.domain.youngPolicy.repository.YoungPolicyRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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
