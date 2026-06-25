package com.team10.backend.domain.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.transaction.dto.req.TransactionHistorySearchReq;
import com.team10.backend.domain.transaction.dto.res.TransactionHistoryDetailRes;
import com.team10.backend.domain.transaction.dto.res.TransactionHistorySearchRes;
import com.team10.backend.domain.transaction.exception.TransactionHistoryErrorCode;
import com.team10.backend.domain.transaction.service.TransactionHistoryService;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionHistoryController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser(userId = 10L)
class TransactionHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionHistoryService transactionHistoryService;

    @Test
    @DisplayName("다건 조회 성공 시 Page 응답 구조를 반환한다")
    void getTransactionHistoriesSucceedsAndReturnsPageStructure() throws Exception {
        TransactionHistorySearchRes response = new TransactionHistorySearchRes(
                1L,
                "홍길동",
                5_000L,
                95_000L,
                LocalDateTime.of(2026, 6, 9, 12, 30),
                TransactionDirection.OUT
        );
        given(transactionHistoryService.getTransactionHistories(
                eq(1L),
                eq(10L),
                any(TransactionHistorySearchReq.class),
                eq(0),
                eq(Sort.Direction.DESC)
        )).willReturn(new PageImpl<>(
                List.of(response),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "transactedAt")),
                1
        ));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].transactionHistoryId").value(1))
                .andExpect(jsonPath("$.content[0].counterpartyName").value("홍길동"))
                .andExpect(jsonPath("$.content[0].amount").value(5000))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(95000))
                .andExpect(jsonPath("$.content[0].transactedAt").value("2026-06-09T12:30:00"))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"))
                .andExpect(jsonPath("$.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.pageable.pageSize").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(transactionHistoryService).getTransactionHistories(
                eq(1L),
                eq(10L),
                any(TransactionHistorySearchReq.class),
                eq(0),
                eq(Sort.Direction.DESC)
        );
    }

    @Test
    @DisplayName("요청한 page와 정렬 방향을 서비스에 전달한다")
    void getTransactionHistoriesPassesCustomPagingAndSortDirection() throws Exception {
        given(transactionHistoryService.getTransactionHistories(
                eq(1L),
                eq(10L),
                any(TransactionHistorySearchReq.class),
                eq(2),
                eq(Sort.Direction.ASC)
        )).willReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(2, 20, Sort.by(Sort.Direction.ASC, "transactedAt")),
                0
        ));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("page", "2")
                        .param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.pageable.pageNumber").value(2));

        verify(transactionHistoryService).getTransactionHistories(
                eq(1L),
                eq(10L),
                any(TransactionHistorySearchReq.class),
                eq(2),
                eq(Sort.Direction.ASC)
        );
    }

    @Test
    @DisplayName("다건 조회 필터 파라미터를 서비스에 전달한다")
    void getTransactionHistoriesPassesFilterParameters() throws Exception {
        given(transactionHistoryService.getTransactionHistories(
                eq(1L),
                eq(10L),
                any(TransactionHistorySearchReq.class),
                eq(0),
                eq(Sort.Direction.DESC)
        )).willReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "transactedAt")),
                0
        ));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-09")
                        .param("direction", "OUT")
                        .param("minAmount", "1000")
                        .param("maxAmount", "10000")
                        .param("counterpartyName", "홍길동"))
                .andExpect(status().isOk());

        ArgumentCaptor<TransactionHistorySearchReq> filterCaptor =
                ArgumentCaptor.forClass(TransactionHistorySearchReq.class);
        verify(transactionHistoryService).getTransactionHistories(
                eq(1L),
                eq(10L),
                filterCaptor.capture(),
                eq(0),
                eq(Sort.Direction.DESC)
        );

        TransactionHistorySearchReq filter = filterCaptor.getValue();
        assertThat(filter.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(filter.endDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(filter.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(filter.minAmount()).isEqualTo(1_000L);
        assertThat(filter.maxAmount()).isEqualTo(10_000L);
        assertThat(filter.counterpartyName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("단건 조회 성공 시 상세 응답 구조를 반환한다")
    void getTransactionHistoryDetailSucceedsAndReturnsDetailStructure() throws Exception {
        TransactionHistoryDetailRes response = new TransactionHistoryDetailRes(
                100L,
                TransactionType.TRANSFER,
                TransactionDirection.OUT,
                5_000L,
                95_000L,
                "홍길동",
                "점심값",
                LocalDateTime.of(2026, 6, 9, 12, 30)
        );
        given(transactionHistoryService.getTransactionHistoryDetail(1L, 100L, 10L))
                .willReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions/{transactionId}", 1L, 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionHistoryId").value(100))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.direction").value("OUT"))
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.balanceAfter").value(95000))
                .andExpect(jsonPath("$.counterpartyName").value("홍길동"))
                .andExpect(jsonPath("$.memo").value("점심값"))
                .andExpect(jsonPath("$.transactedAt").value("2026-06-09T12:30:00"));

        verify(transactionHistoryService).getTransactionHistoryDetail(1L, 100L, 10L);
    }

    @Test
    @DisplayName("단건 조회 권한이 없으면 403을 반환한다")
    void getTransactionHistoryDetailReturnsForbiddenWhenAccountAccessDenied() throws Exception {
        given(transactionHistoryService.getTransactionHistoryDetail(1L, 100L, 10L))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions/{transactionId}", 1L, 100L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("단건 거래내역이 없으면 404를 반환한다")
    void getTransactionHistoryDetailReturnsNotFoundWhenTransactionHistoryDoesNotExist() throws Exception {
        given(transactionHistoryService.getTransactionHistoryDetail(1L, 100L, 10L))
                .willThrow(new BusinessException(TransactionHistoryErrorCode.TRANSACTION_HISTORY_NOT_FOUND));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions/{transactionId}", 1L, 100L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("단건 조회 transactionId가 양수가 아니면 400을 반환한다")
    void invalidWhenTransactionIdIsNotPositive() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions/{transactionId}", 1L, 0L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("accountId가 양수가 아니면 400을 반환한다")
    void invalidWhenAccountIdIsNotPositive() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 0L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("page가 음수이면 400을 반환한다")
    void invalidWhenPageIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정렬 방향 값이 ASC 또는 DESC가 아니면 400을 반환한다")
    void invalidWhenSortDirectionIsNotEnumValue() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("sortDirection", "LATEST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("입출금 방향 값이 IN 또는 OUT이 아니면 400을 반환한다")
    void invalidWhenDirectionIsNotEnumValue() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("direction", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("시작일이 종료일보다 이후이면 400을 반환한다")
    void invalidWhenStartDateIsAfterEndDate() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("startDate", "2026-06-10")
                        .param("endDate", "2026-06-09"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("최소 거래액이 최대 거래액보다 크면 400을 반환한다")
    void invalidWhenMinAmountIsGreaterThanMaxAmount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("minAmount", "10000")
                        .param("maxAmount", "1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("최소 거래액이 음수이면 400을 반환한다")
    void invalidWhenMinAmountIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("minAmount", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("최대 거래액이 음수이면 400을 반환한다")
    void invalidWhenMaxAmountIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("maxAmount", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("거래 상대명이 30자를 초과하면 400을 반환한다")
    void invalidWhenCounterpartyNameIsTooLong() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", 1L)
                        .param("counterpartyName", "a".repeat(31)))
                .andExpect(status().isBadRequest());
    }
}
