package com.team10.backend.domain.codef.exAccount.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.team10.backend.domain.codef.exAccount.client.CodefExAccountClient;
import com.team10.backend.domain.codef.exAccount.crypto.CodefConnectedIdEncryptor;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionPayload;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionResult;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountListRequest;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;
import com.team10.backend.domain.codef.exAccount.mapper.CodefExAccountConnectionPayloadMapper;
import com.team10.backend.domain.codef.exAccount.mapper.CodefExAccountSnapshotMapper;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 외부 기관 등록 및 보유 계좌 실시간 조회를 담당하는 CODEF 클라이언트 게이트웨이 컴포넌트입니다.
 */
@Component
@RequiredArgsConstructor
public class CodefExAccountGateway {

    private final CodefExAccountConnectionPayloadMapper connectionPayloadMapper;
    private final CodefExAccountClient codefExAccountClient;
    private final CodefConnectedIdEncryptor connectedIdEncryptor;
    private final CodefExAccountSnapshotMapper snapshotMapper;

    /**
     * CODEF API에 사용자의 기관 인증 정보를 전송하여 계정을 등록하고, 발급된 connectedId를 암호화하여 반환합니다.
     *
     * @param request 기관 연결 정보 요청 DTO
     * @return 암호화된 connectedId 래퍼 객체
     */
    public EncryptedConnectedId register(CodefExAccountConnectionCreateReq request) {
        CodefExAccountConnectionPayload payload = connectionPayloadMapper.toPayload(request);
        CodefExAccountConnectionResult result = codefExAccountClient.createConnection(payload);
        return connectedIdEncryptor.encrypt(result.connectedId());
    }

    /**
     * 특정 기관의 암호화된 connectedId를 복호화해 실시간 보유 계좌 목록 정보를 가져와 DTO 목록으로 변환합니다.
     *
     * @param organization        금융기관 코드
     * @param encryptedConnectedId 암호화된 connectedId
     * @return 실시간 보유 계좌 스냅샷 정보 리스트
     */
    public List<CodefExAccountSnapshot> getAccountSnapshots(
            String organization,
            EncryptedConnectedId encryptedConnectedId
    ) {
        String connectedId = connectedIdEncryptor.decrypt(encryptedConnectedId);
        CodefExAccountListRequest request = CodefExAccountListRequest.of(
                organization,
                connectedId,
                ""
        );
        JsonNode data = codefExAccountClient.getAccountList(request);
        return snapshotMapper.toSnapshots(organization, data);
    }
}
