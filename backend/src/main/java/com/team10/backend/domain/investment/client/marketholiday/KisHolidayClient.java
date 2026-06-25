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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team10.backend.domain.investment.client.auth.KisAccessTokenManager;
import com.team10.backend.domain.investment.client.marketholiday.dto.KisHolidayRow;
import com.team10.backend.domain.investment.config.KisProperties;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import com.team10.backend.global.exception.BusinessException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * KIS의 휴장일 정보 API를 호출해 날짜 별 데이터를 가져온다
 */
@Component
@RequiredArgsConstructor
public class KisHolidayClient {

    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String SUCCESS_CODE = "0";
    private static final String OPEN_YN = "Y";

    private final RestClient restClient;
    private final KisProperties properties;
    private final KisAccessTokenManager accessTokenManager;

    /**
     * 휴장일 조회 API를 호출하고 정제하여 반환한다
     */
    public List<KisHolidayRow> fetchHolidays(MarketType marketType, LocalDate baseDate) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + MARKET_HOLIDAY_PATH)
                .queryParam("BASS_DT", baseDate.format(REQUEST_DATE_FORMATTER))
                .queryParam("CTX_AREA_NK", "")
                .queryParam("CTX_AREA_FK", "")
                .build()
                .toUri();

        KisHolidayRes response = restClient.get()
                .uri(uri)
                .header(AUTHORIZATION, "Bearer " + accessTokenManager.getAccessToken())
                .header(APP_KEY, properties.appKey())
                .header(APP_SECRET, properties.appSecret())
                .header(TR_ID, MARKET_HOLIDAY_TR_ID)
                .header(CUST_TYPE, "P")
                .header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + "; charset=utf-8")
                .retrieve()
                .body(KisHolidayRes.class);

        if (response == null
                || !SUCCESS_CODE.equals(response.resultCode())
                || response.output() == null) {
            throw new BusinessException(InvestmentErrorCode.KIS_API_FAILED);
        }

        return response.output().stream()
                .map(output -> new KisHolidayRow(
                        marketType,
                        LocalDate.parse(output.baseDate(), REQUEST_DATE_FORMATTER),
                        OPEN_YN.equals(output.openYn())
                ))
                .toList();
    }

    private record KisHolidayRes(
            @JsonProperty("rt_cd") String resultCode,
            @JsonProperty("msg_cd") String messageCode,
            @JsonProperty("msg1") String message,
            List<KisHolidayOutput> output
    ) {
    }

    private record KisHolidayOutput(
            @JsonProperty("bass_dt") String baseDate,
            @JsonProperty("opnd_yn") String openYn
    ) {
    }
}
