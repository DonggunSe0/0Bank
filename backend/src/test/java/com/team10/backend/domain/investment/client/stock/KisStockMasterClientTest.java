package com.team10.backend.domain.investment.client.stock;

import static com.team10.backend.domain.investment.config.KisConstants.StockMaster.KOSPI_MASTER_DOWNLOAD_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KisStockMasterClientTest {

    private MockRestServiceServer server;
    private KisStockMasterClient stockMasterClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        stockMasterClient = new KisStockMasterClient(builder.build());
    }

    @Test
    @DisplayName("KIS 종목 마스터 파일 다운로드 URL에서 zip 원본 바이트를 가져온다")
    void downloadKospiMasterFile() {
        byte[] expected = new byte[]{1, 2, 3};
        server.expect(requestTo(KOSPI_MASTER_DOWNLOAD_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(expected, MediaType.APPLICATION_OCTET_STREAM));

        byte[] actual = stockMasterClient.downloadKospiMasterFile();

        assertThat(actual).containsExactly(expected);
        server.verify();
    }

    @Test
    @DisplayName("종목 마스터 다운로드 응답이 비어있으면 API 실패 예외를 던진다")
    void downloadKospiMasterFileFailsWhenBodyIsEmpty() {
        server.expect(requestTo(KOSPI_MASTER_DOWNLOAD_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(() -> stockMasterClient.downloadKospiMasterFile())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.KIS_API_FAILED));
        server.verify();
    }
}
