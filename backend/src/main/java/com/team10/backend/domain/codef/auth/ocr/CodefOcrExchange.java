package com.team10.backend.domain.codef.auth.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team10.backend.domain.codef.auth.client.CodefApiResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/** CODEF 신분증 OCR API 선언적 HTTP 인터페이스. */
public interface CodefOcrExchange {

    @PostExchange(url = "/v1/kr/etc/a/ocr/registration-card", contentType = MediaType.APPLICATION_JSON_VALUE)
    String requestOcr(@RequestBody CodefOcrRequest request);

    record CodefOcrRequest(
            @JsonProperty("Type") String type,
            @JsonProperty("secret_mode") String secretMode,
            @JsonProperty("IdCard_base64") String idCardBase64,
            @JsonProperty("image_return") String imageReturn,
            @JsonProperty("image_save") String imageSave
    ) {}

    // CODEF 응답은 URLDecoder.decode 후에야 JSON이 되므로(CodefOcrClient 참고)
    // Exchange의 리턴 타입 자체는 String을 유지하고, 이 레코드들은 디코딩 후 수동 파싱에만 쓰인다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CodefOcrResponse(CodefApiResult result, CodefOcrData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CodefOcrData(
            @JsonProperty("resUserName") String resUserName,
            @JsonProperty("resUserIdentity") String resUserIdentity,
            @JsonProperty("resIssueDate") String resIssueDate
    ) {}
}
