package com.team10.backend.domain.codef.exAccount.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.exAccount.client.CodefExAccountClient;
import com.team10.backend.domain.codef.exAccount.crypto.CodefConnectedIdEncryptor;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionPayload;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionResult;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountListRequest;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountTransactionListRequest;
import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;
import com.team10.backend.domain.codef.exAccount.mapper.CodefExAccountConnectionPayloadMapper;
import com.team10.backend.domain.codef.exAccount.mapper.CodefExAccountSnapshotMapper;
import com.team10.backend.domain.codef.exAccount.mapper.CodefExAccountTransactionMapper;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodefExAccountGatewayTest {

    @Mock
    private CodefExAccountConnectionPayloadMapper connectionPayloadMapper;
    @Mock
    private CodefExAccountClient codefExAccountClient;
    @Mock
    private CodefConnectedIdEncryptor connectedIdEncryptor;
    @Mock
    private CodefExAccountSnapshotMapper snapshotMapper;
    @Mock
    private CodefExAccountTransactionMapper transactionMapper;

    private CodefExAccountGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new CodefExAccountGateway(
                connectionPayloadMapper,
                codefExAccountClient,
                connectedIdEncryptor,
                snapshotMapper,
                transactionMapper
        );
    }

    @Test
    void encryptsConnectedIdImmediatelyAfterRegistration() {
        CodefExAccountConnectionCreateReq request = request();
        CodefExAccountConnectionPayload payload = new CodefExAccountConnectionPayload(List.of());
        EncryptedConnectedId encrypted = new EncryptedConnectedId("ciphertext", "iv", "v1");
        when(connectionPayloadMapper.toPayload(request)).thenReturn(payload);
        when(codefExAccountClient.createConnection(payload))
                .thenReturn(new CodefExAccountConnectionResult("plain-connected-id"));
        when(connectedIdEncryptor.encrypt("plain-connected-id")).thenReturn(encrypted);

        assertThat(gateway.register(request)).isEqualTo(encrypted);
        verify(connectedIdEncryptor).encrypt("plain-connected-id");
    }

    @Test
    void decryptsConnectedIdOnlyForProviderAccountRequest() throws Exception {
        EncryptedConnectedId encrypted = new EncryptedConnectedId("ciphertext", "iv", "v1");
        JsonNode data = new ObjectMapper().readTree("{}");
        when(connectedIdEncryptor.decrypt(encrypted)).thenReturn("plain-connected-id");
        when(codefExAccountClient.getAccountList(any(CodefExAccountListRequest.class)))
                .thenReturn(data);
        when(snapshotMapper.toSnapshots("0004", data)).thenReturn(List.of());

        gateway.getAccountSnapshots("0004", encrypted, "950101");

        ArgumentCaptor<CodefExAccountListRequest> captor =
                ArgumentCaptor.forClass(CodefExAccountListRequest.class);
        verify(codefExAccountClient).getAccountList(captor.capture());
        assertThat(captor.getValue().connectedId()).isEqualTo("plain-connected-id");
        assertThat(captor.getValue().birthDate()).isEqualTo("950101");
        assertThat(captor.getValue().toString()).doesNotContain("plain-connected-id");
    }

    @Test
    void decryptsConnectedIdOnlyForProviderTransactionRequest() throws Exception {
        EncryptedConnectedId encrypted = new EncryptedConnectedId("ciphertext", "iv", "v1");
        JsonNode data = new ObjectMapper().readTree("{}");
        when(connectedIdEncryptor.decrypt(encrypted)).thenReturn("plain-connected-id");
        when(codefExAccountClient.getTransactionList(any(CodefExAccountTransactionListRequest.class)))
                .thenReturn(data);
        when(transactionMapper.toSnapshots("0004", "1234567890", data)).thenReturn(List.of());

        gateway.getTransactionSnapshots(
                "0004",
                encrypted,
                "950101",
                "1234567890",
                java.time.LocalDate.of(2026, 6, 1),
                java.time.LocalDate.of(2026, 6, 30)
        );

        ArgumentCaptor<CodefExAccountTransactionListRequest> captor =
                ArgumentCaptor.forClass(CodefExAccountTransactionListRequest.class);
        verify(codefExAccountClient).getTransactionList(captor.capture());
        assertThat(captor.getValue().connectedId()).isEqualTo("plain-connected-id");
        assertThat(captor.getValue().account()).isEqualTo("1234567890");
        assertThat(captor.getValue().startDate()).isEqualTo("20260601");
        assertThat(captor.getValue().endDate()).isEqualTo("20260630");
        assertThat(captor.getValue().toString()).doesNotContain("plain-connected-id");
        assertThat(captor.getValue().toString()).doesNotContain("1234567890");
    }

    private CodefExAccountConnectionCreateReq request() {
        return new CodefExAccountConnectionCreateReq(
                "0004", "BK", "P", "1", "internet-user", "bank-password", "950101"
        );
    }
}
