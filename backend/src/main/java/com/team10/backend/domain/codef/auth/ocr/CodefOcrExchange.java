package com.team10.backend.domain.codef.auth.ocr;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

/** CODEF 신분증 OCR API 선언적 HTTP 인터페이스. */
public interface CodefOcrExchange {

    @PostExchange(url = "/v1/kr/etc/a/ocr/registration-card", contentType = MediaType.APPLICATION_JSON_VALUE)
    String requestOcr(@RequestBody Map<String, String> body);
}
