package com.team10.backend.domain.investment.client.marketholiday;

import static com.team10.backend.domain.investment.config.KisConstants.BASE_URL;
import static com.team10.backend.domain.investment.config.KisConstants.Header.APP_KEY;
import static com.team10.backend.domain.investment.config.KisConstants.Header.APP_SECRET;
import static com.team10.backend.domain.investment.config.KisConstants.Header.AUTHORIZATION;
import static com.team10.backend.domain.investment.config.KisConstants.Header.CONTENT_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.Header.CUST_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.Header.TR_ID;
import static com.team10.backend.domain.investment.config.KisConstants.MarketHoliday.MARKET_HOLIDAY_PATH;
import static com.team10.backend.domain.investment.config.KisConstants.MarketHoliday.MARKET_HOLIDAY_TR_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.team10.backend.domain.investment.client.auth.KisAccessTokenManager;
import com.team10.backend.domain.investment.client.marketholiday.dto.KisHolidayRow;
import com.team10.backend.domain.investment.config.KisProperties;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import com.team10.backend.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KisHolidayClientTest {

    @Mock
    private KisAccessTokenManager accessTokenManager;

    private MockRestServiceServer server;
    private KisHolidayClient kisHolidayClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        kisHolidayClient = new KisHolidayClient(
                builder.build(),
                new KisProperties("app-key", "app-secret", 41),
                accessTokenManager
        );
    }

    @Test
    @DisplayName("휴장일 조회 API를 호출하고 개장 여부를 파싱한다")
    void fetchHolidays() {
        when(accessTokenManager.getAccessToken()).thenReturn("access-token");
        server.expect(requestTo(BASE_URL + MARKET_HOLIDAY_PATH
                        + "?BASS_DT=20260616&CTX_AREA_NK=&CTX_AREA_FK="))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AUTHORIZATION, "Bearer access-token"))
                .andExpect(header(APP_KEY, "app-key"))
                .andExpect(header(APP_SECRET, "app-secret"))
                .andExpect(header(TR_ID, MARKET_HOLIDAY_TR_ID))
                .andExpect(header(CUST_TYPE, "P"))
                .andExpect(header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + "; charset=utf-8"))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "msg1": "정상처리 되었습니다.",
                          "output": [
                            {
                              "bass_dt": "20260616",
                              "opnd_yn": "Y"
                            },
                            {
                              "bass_dt": "20260617",
                              "opnd_yn": "N"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<KisHolidayRow> rows = kisHolidayClient.fetchHolidays(
                MarketType.KRX,
                LocalDate.of(2026, 6, 16)
        );

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).marketType()).isEqualTo(MarketType.KRX);
        assertThat(rows.get(0).holidayDate()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(rows.get(0).isOpen()).isTrue();
        assertThat(rows.get(1).marketType()).isEqualTo(MarketType.KRX);
        assertThat(rows.get(1).holidayDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(rows.get(1).isOpen()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("휴장일 조회 API 응답 코드가 실패이면 API 실패 예외를 던진다")
    void fetchHolidaysFailsWhenResponseCodeIsNotSuccess() {
        when(accessTokenManager.getAccessToken()).thenReturn("access-token");
        server.expect(requestTo(BASE_URL + MARKET_HOLIDAY_PATH
                        + "?BASS_DT=20260616&CTX_AREA_NK=&CTX_AREA_FK="))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "1",
                          "msg_cd": "ERROR",
                          "msg1": "오류",
                          "output": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kisHolidayClient.fetchHolidays(
                MarketType.KRX,
                LocalDate.of(2026, 6, 16)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.KIS_API_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("휴장일 조회 API 응답에 output이 없으면 API 실패 예외를 던진다")
    void fetchHolidaysFailsWhenOutputIsMissing() {
        when(accessTokenManager.getAccessToken()).thenReturn("access-token");
        server.expect(requestTo(BASE_URL + MARKET_HOLIDAY_PATH
                        + "?BASS_DT=20260616&CTX_AREA_NK=&CTX_AREA_FK="))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "msg1": "정상처리 되었습니다."
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kisHolidayClient.fetchHolidays(
                MarketType.KRX,
                LocalDate.of(2026, 6, 16)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.KIS_API_FAILED));
        server.verify();
    }
}
