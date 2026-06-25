package com.team10.backend.domain.exchange.client;

import com.team10.backend.domain.exchange.type.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UpbitExchangeRateClient {

    private final RestClient upbitRestClient;

    public List<UpbitExchangeRateRes> fetch(List<CurrencyCode> currencyCodes) {
        // codes=FRX.KRWUSD,FRX.KRWJPY,FRX.KRWEUR,FRX.KRWIDR
        String codes = currencyCodes.stream()
                .map(code -> "FRX.KRW" + code)
                .collect(Collectors.joining(","));

        return upbitRestClient.get() // HTTP GET 요청 시작
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/forex/recent") // 기본 베이스 URL 뒤에 붙을 상세 경로를 지정
                        .queryParam("codes", codes) // 쿼리 스트링 파라미터 추가
                        .build())
                .retrieve() // 설정한 URI로 실제 HTTP 요청을 보내고, 서버로부터 응답을 받아옴
                .body(new ParameterizedTypeReference<>() {}); // 받아온 HTTP 응답 본문을 원하는 자바 객체 타입으로 역직렬화하여 반환
    }

}
