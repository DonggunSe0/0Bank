package com.team10.backend.domain.codef.auth.client;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

/** CODEF 1원 송금(계좌인증) API 선언적 HTTP 인터페이스. */
public interface CodefBankTransferExchange {

    @PostExchange(url = "/v1/kr/bank/a/account/transfer-authentication", contentType = MediaType.APPLICATION_JSON_VALUE)
    String requestTransfer(@RequestBody Map<String, Object> body);
}
