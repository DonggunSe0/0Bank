package com.team10.backend.domain.codef.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;

/** CODEF OAuth 토큰 발급 API 선언적 HTTP 인터페이스. */
public interface CodefOAuthExchange {

    @PostExchange(url = "https://oauth.codef.io/oauth/token",
            contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    CodefTokenResponse issueToken(@RequestHeader("Authorization") String basicAuthHeader,
                                   @RequestBody String formBody);

    record CodefTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {}
}
