package com.team10.backend.domain.codef.exAccount.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.codef.exAccount.config.CodefExAccountProperties;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionPayload;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionResult;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountListRequest;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountClientException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationException;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountRegistrationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CodefExAccountClientTest {

    private static final String BASE_URL = "https://development.codef.io";
    private static final String ACCOUNT_CREATE_PATH = "/v1/account/create";
    private static final String ACCOUNT_LIST_PATH = "/v1/kr/bank/p/account/account-list";

    private MockRestServiceServer server;
    private CodefExAccountClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        CodefExAccountAuthClient authClient = mock(CodefExAccountAuthClient.class);
        when(authClient.getAccessToken()).thenReturn("account-access-token");
        client = new CodefExAccountClient(
                properties(),
                authClient,
                new CodefExAccountResponseDecoder(new ObjectMapper()),
                builder.build()
        );
    }

    @Test
    void requestsAccountListWithConnectedIdAndDecodesResponse() {
        String response = URLEncoder.encode("""
                {
                  "result": {"code": "CF-00000", "message": "성공"},
                  "data": {
                    "resDepositTrust": {
                      "resAccount": "1234567890",
                      "resAccountName": "입출금통장"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);
        server.expect(requestTo(BASE_URL + ACCOUNT_LIST_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer account-access-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "organization": "0020",
                          "connectedId": "3Lj7J-OvQ",
                          "birthDate": "990101",
                          "withdrawAccountNo": "",
                          "withdrawAccountPassword": ""
                        }
                        """))
                .andRespond(withSuccess(response, MediaType.TEXT_PLAIN));

        JsonNode data = client.getAccountList(
                CodefExAccountListRequest.of("0020", "3Lj7J-OvQ", "990101"));

        assertThat(data.path("resDepositTrust").path("resAccount").asText())
                .isEqualTo("1234567890");
        server.verify();
    }

    @Test
    void createsAccountAndReturnsConnectedId() {
        String response = URLEncoder.encode("""
                {
                  "result": {"code": "CF-00000", "message": "정상"},
                  "data": {
                    "successList": [{"code": "CF-00000", "organization": "0004"}],
                    "errorList": [],
                    "connectedId": "issued-connected-id"
                  }
                }
                """, StandardCharsets.UTF_8);
        server.expect(requestTo(BASE_URL + ACCOUNT_CREATE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer account-access-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "accountList": [{
                            "countryCode": "KR",
                            "businessType": "BK",
                            "clientType": "P",
                            "organization": "0004",
                            "loginType": "1",
                            "id": "internet-user",
                            "password": "rsa-encrypted-password",
                            "birthDate": "990101"
                          }]
                        }
                        """))
                .andRespond(withSuccess(response, MediaType.TEXT_PLAIN));

        CodefExAccountConnectionResult result = client.createConnection(connectionPayload());

        assertThat(result.connectedId()).isEqualTo("issued-connected-id");
        assertThat(result.toString()).doesNotContain("issued-connected-id");
        server.verify();
    }

    @Test
    void classifiesCreateAccountServerError() {
        server.expect(requestTo(BASE_URL + ACCOUNT_CREATE_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE_URL + ACCOUNT_CREATE_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.createConnection(connectionPayload()))
                .isInstanceOfSatisfying(CodefExAccountRegistrationException.class, exception -> {
                    assertThat(exception.getFailure())
                            .isEqualTo(CodefExAccountRegistrationFailure.SYSTEM_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("CODEF 계정등록 HTTP 요청에 실패했습니다.");
                });
        server.verify();
    }

    @Test
    void retriesServerErrorOnlyOnce() {
        server.expect(requestTo(BASE_URL + ACCOUNT_LIST_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE_URL + ACCOUNT_LIST_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getAccountList(validRequest()))
                .isInstanceOf(CodefExAccountClientException.class)
                .hasMessage("CODEF 보유계좌 HTTP 요청에 실패했습니다.");
        server.verify();
    }

    @Test
    void retriesNetworkAccessFailureOnceAndReturnsAccountList() {
        String response = URLEncoder.encode("""
                {
                  "result": {"code": "CF-00000", "message": "성공"},
                  "data": {"resDepositTrust": {"resAccount": "1234567890"}}
                }
                """, StandardCharsets.UTF_8);
        server.expect(requestTo(BASE_URL + ACCOUNT_LIST_PATH))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        server.expect(requestTo(BASE_URL + ACCOUNT_LIST_PATH))
                .andRespond(withSuccess(response, MediaType.TEXT_PLAIN));

        JsonNode data = client.getAccountList(validRequest());

        assertThat(data.path("resDepositTrust").path("resAccount").asText())
                .isEqualTo("1234567890");
        server.verify();
    }

    @Test
    void doesNotRetryAuthenticationError() {
        server.expect(requestTo(BASE_URL + ACCOUNT_LIST_PATH))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getAccountList(validRequest()))
                .isInstanceOf(CodefExAccountClientException.class)
                .hasMessage("CODEF 보유계좌 HTTP 요청에 실패했습니다.");
        server.verify();
    }

    @Test
    void doesNotRetryRateLimitResponseFromCodef() {
        server.expect(requestTo(BASE_URL + ACCOUNT_LIST_PATH))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.getAccountList(validRequest()))
                .isInstanceOf(CodefExAccountClientException.class)
                .hasMessage("CODEF 보유계좌 HTTP 요청에 실패했습니다.");
        server.verify();
    }

    @Test
    void doesNotRetryCreateConnectionClientError() {
        server.expect(requestTo(BASE_URL + ACCOUNT_CREATE_PATH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.createConnection(connectionPayload()))
                .isInstanceOfSatisfying(CodefExAccountRegistrationException.class, exception -> {
                    assertThat(exception.getFailure())
                            .isEqualTo(CodefExAccountRegistrationFailure.SYSTEM_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("CODEF 계정등록 HTTP 요청에 실패했습니다.");
                });
        server.verify();
    }

    @Test
    void rejectsMissingConnectedIdBeforeHttpRequest() {
        CodefExAccountListRequest request = CodefExAccountListRequest.of("0020", " ", "990101");

        assertThatThrownBy(() -> client.getAccountList(request))
                .isInstanceOf(CodefExAccountClientException.class)
                .hasMessage("CODEF 보유계좌 요청값이 올바르지 않습니다.")
                .hasMessageNotContaining("3Lj7J-OvQ");
        server.verify();
    }

    private CodefExAccountListRequest validRequest() {
        return CodefExAccountListRequest.of("0020", "3Lj7J-OvQ", "990101");
    }

    private CodefExAccountConnectionPayload connectionPayload() {
        return new CodefExAccountConnectionPayload(List.of(
                new CodefExAccountConnectionPayload.Account(
                        "KR", "BK", "P", "0004", "1",
                        "internet-user", "rsa-encrypted-password", "990101"
                )
        ));
    }

    private CodefExAccountProperties properties() {
        return new CodefExAccountProperties(
                "DEMO",
                "account-client-id",
                "account-client-secret",
                "account-public-key",
                BASE_URL,
                ACCOUNT_CREATE_PATH,
                ACCOUNT_LIST_PATH,
                "/v1/kr/bank/p/account/transaction-list"
        );
    }
}
