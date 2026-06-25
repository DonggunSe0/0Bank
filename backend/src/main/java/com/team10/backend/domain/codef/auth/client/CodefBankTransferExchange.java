package com.team10.backend.domain.codef.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/** CODEF 1원 송금(계좌인증) API 선언적 HTTP 인터페이스. */
public interface CodefBankTransferExchange {

    @PostExchange(url = "/v1/kr/bank/a/account/transfer-authentication", contentType = MediaType.APPLICATION_JSON_VALUE)
    String requestTransfer(@RequestBody CodefBankTransferRequest request);

    record CodefBankTransferRequest(
            @JsonProperty("organization") String organization,
            @JsonProperty("account") String account,
            @JsonProperty("inPrintType") String inPrintType,
            @JsonProperty("inPrintContent") String inPrintContent
    ) {}

    // CODEF 응답은 URLDecoder.decode 후에야 JSON이 되므로(CodefBankTransferService 참고)
    // Exchange의 리턴 타입 자체는 String을 유지하고, 이 레코드는 디코딩 후 수동 파싱에만 쓰인다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CodefBankTransferResponse(CodefApiResult result) {}
}
