package com.team10.backend.domain.codef.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** CODEF API 공통 응답의 result 블록(code/message) — 1원송금/OCR 등 여러 엔드포인트가 같은 모양을 공유한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CodefApiResult(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message
) {}
