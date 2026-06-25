package com.team10.backend.domain.transfer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.transfer.dto.req.DepositReq;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.service.TransferService;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class TransferControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TransferService transferService;

    @Test
    @DisplayName("송금 요청 검증 - 금액이 null이면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_amountNull_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, "100200300002", "123456", null, "금액 없음");

        performTransfer(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("송금 요청 검증 - 금액이 0이면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_amountZero_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, "100200300002", "123456", 0L, "0원 송금");

        performTransfer(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("송금 요청 검증 - 금액이 음수이면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_amountNegative_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, "100200300002", "123456", -1L, "음수 송금");

        performTransfer(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("송금 요청 검증 - 수취 계좌번호가 blank이면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_receiverAccountNumberBlank_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, " ", "123456", 50_000L, "blank 계좌번호");

        performTransfer(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("송금 요청 검증 - 수취 계좌번호 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_receiverAccountNumberInvalidFormat_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, "100ABC300002", "123456", 50_000L, "형식 오류");

        performTransfer(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("송금 요청 검증 - 계좌 비밀번호가 blank이면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_accountPasswordBlank_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, "100200300002", " ", 50_000L, "blank 비밀번호");

        performTransfer(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("송금 요청 검증 - 계좌 비밀번호가 숫자 6자리가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_accountPasswordInvalidFormat_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, "100200300002", "abc123", 50_000L, "비밀번호 형식 오류");

        performTransfer(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("송금 요청 검증 - Idempotency-Key 헤더가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void transfer_missingIdempotencyKeyHeader_returnsBadRequest() throws Exception {
        TransferReq request = new TransferReq(1L, "100200300002", "123456", 50_000L, "키 누락");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("입금 요청 검증 - 금액이 null이면 400을 반환하고 서비스를 호출하지 않는다")
    void topUp_amountNull_returnsBadRequest() throws Exception {
        DepositReq request = new DepositReq(1L, null, "금액 없음");

        performTopUp(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("입금 요청 검증 - 금액이 0이면 400을 반환하고 서비스를 호출하지 않는다")
    void topUp_amountZero_returnsBadRequest() throws Exception {
        DepositReq request = new DepositReq(1L, 0L, "0원 입금");

        performTopUp(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("입금 요청 검증 - 금액이 음수이면 400을 반환하고 서비스를 호출하지 않는다")
    void topUp_amountNegative_returnsBadRequest() throws Exception {
        DepositReq request = new DepositReq(1L, -1L, "음수 입금");

        performTopUp(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("입금 요청 검증 - accountId가 null이면 400을 반환하고 서비스를 호출하지 않는다")
    void topUp_accountIdNull_returnsBadRequest() throws Exception {
        DepositReq request = new DepositReq(null, 50_000L, "계좌 없음");

        performTopUp(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("입금 요청 검증 - Idempotency-Key 헤더가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void topUp_missingIdempotencyKeyHeader_returnsBadRequest() throws Exception {
        DepositReq request = new DepositReq(1L, 50_000L, "키 누락");

        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    @Test
    @DisplayName("입금 요청 검증 - memo가 100자를 초과하면 400을 반환하고 서비스를 호출하지 않는다")
    void topUp_memoTooLong_returnsBadRequest() throws Exception {
        DepositReq request = new DepositReq(1L, 50_000L, "a".repeat(101));

        performTopUp(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyTransferServiceNeverCalled();
    }

    private org.springframework.test.web.servlet.ResultActions performTransfer(TransferReq request) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", "validation-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions performTopUp(DepositReq request) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers/topUp")
                .header("Idempotency-Key", "validation-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void verifyTransferServiceNeverCalled() {
        verify(transferService, never()).topUp(
                anyLong(),
                anyLong(),
                any(),
                any()
        );
        verify(transferService, never()).transfer(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                any(),
                any()
        );
    }
}
