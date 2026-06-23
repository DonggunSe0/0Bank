package com.team10.backend.domain.investment.client.stock;

import static com.team10.backend.domain.investment.config.KisConstants.StockMaster.KOSPI_MASTER_DOWNLOAD_URL;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KisStockMasterClient {

    private final RestClient restClient;

    public byte[] downloadKospiMasterFile() {
        /**
         * 종목 마스터 파일은 KIS OpenAPI 인증 API가 아니라 별도 다운로드 서버에서 제공된다.
         * AccessToken, appkey 헤더 없이 zip 원본 바이트를 그대로 받아 파서로 넘긴다.
         */
        byte[] body = restClient.get()
                .uri(KOSPI_MASTER_DOWNLOAD_URL)
                .retrieve()
                .body(byte[].class);

        if (body == null || body.length == 0) {
            throw new BusinessException(InvestmentErrorCode.KIS_API_FAILED);
        }

        return body;
    }
}
