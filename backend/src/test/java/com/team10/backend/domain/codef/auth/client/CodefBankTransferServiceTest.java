package com.team10.backend.domain.codef.auth.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * HTTP 호출 자체는 {@link CodefBankTransferExchange}로 위임됐다(검증은 {@code CodefBankRestClientConfigTest} 참고).
 * 여기서는 CodefBankTransferService의 URL-decode + result.code 판정 + 로그 마스킹만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CodefBankTransferServiceTest {

    @Mock
    CodefBankTransferExchange codefBankTransferExchange;

    @InjectMocks
    CodefBankTransferService service;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUpLogCapture() {
        appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(CodefBankTransferService.class);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDownLogCapture() {
        ((Logger) LoggerFactory.getLogger(CodefBankTransferService.class)).detachAppender(appender);
    }

    private String logMessages() {
        StringBuilder sb = new StringBuilder();
        appender.list.forEach(event -> sb.append(event.getFormattedMessage()).append('\n'));
        return sb.toString();
    }

    @Nested
    @DisplayName("sendOneWon")
    class SendOneWon {

        @Test
        @DisplayName("정상 송금 — 예외 없이 완료")
        void success() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenReturn("{\"result\":{\"code\":\"CF-00000\",\"message\":\"SUCCESS\"}}");

            assertThatCode(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CODEF 실패 코드 응답 → ONE_WON_TRANSFER_FAILED")
        void failureCode() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenReturn("{\"result\":{\"code\":\"CF-03002\",\"message\":\"계좌번호 오류\"}}");

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        @Test
        @DisplayName("응답 본문이 JSON null → ONE_WON_TRANSFER_FAILED")
        void nullResponseBody() {
            when(codefBankTransferExchange.requestTransfer(any())).thenReturn("null");

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        @Test
        @DisplayName("result 필드 누락 → ONE_WON_TRANSFER_FAILED")
        void missingResultField() {
            when(codefBankTransferExchange.requestTransfer(any())).thenReturn("{\"data\":{}}");

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        @Test
        @DisplayName("result 필드가 객체가 아닌 다른 타입 → ONE_WON_TRANSFER_FAILED (200 OK 응답이 예상과 다른 모양인 케이스)")
        void resultFieldWrongType() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenReturn("{\"result\":\"unexpected-string-value\"}");

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        @Test
        @DisplayName("토큰 응답 파싱 실패(CodefAuthException) → ONE_WON_TRANSFER_FAILED로 변환")
        void authFailure() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenThrow(new CodefAuthException("토큰 파싱 실패", new RuntimeException()));

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }

        @Test
        @DisplayName("토큰 발급 자체가 실패(BusinessException — CodefHttpServiceConfig의 defaultStatusHandler가 변환)하면 ONE_WON_TRANSFER_FAILED로 덮어쓰지 않고 그대로 전파한다")
        void tokenIssueFailure_propagatesAsIs() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenThrow(new BusinessException(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED));

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED);
        }

        @Test
        @DisplayName("HTTP 호출 자체 실패 → ONE_WON_TRANSFER_FAILED로 변환")
        void httpCallFailure() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenThrow(new RestClientException("read timeout"));

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);
        }
    }

    @Nested
    @DisplayName("로그 마스킹")
    class LoggingMasking {

        @Test
        @DisplayName("송금 성공 로그 — 계좌번호는 마스킹되고, 인증코드(verificationCode)는 시연 목적상 그대로 남는다")
        void success_masksAccountNumberButKeepsVerificationCodeVisible() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenReturn("{\"result\":{\"code\":\"CF-00000\",\"message\":\"SUCCESS\"}}");

            service.sendOneWon("004", "12345678901", "9999");

            String logs = logMessages();
            assertThat(logs).doesNotContain("12345678901");
            assertThat(logs).contains("123456*8901"); // prefix(6)+*+suffix(4) — 계좌번호 "12345678901"의 마스킹 결과
            assertThat(logs).contains("verificationCode=9999"); // 인증코드는 계좌번호와 별개로 그대로 노출되어야 함
        }

        @Test
        @DisplayName("송금 실패 로그 — 계좌번호는 마스킹된다")
        void failureCode_masksAccountNumber() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenReturn("{\"result\":{\"code\":\"CF-03002\",\"message\":\"계좌번호 오류\"}}");

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "9999"))
                    .isInstanceOf(BusinessException.class);

            String logs = logMessages();
            assertThat(logs).doesNotContain("12345678901");
            assertThat(logs).contains("123456*8901");
        }

        @Test
        @DisplayName("HTTP 호출 자체 실패 로그 — 계좌번호는 마스킹된다")
        void httpCallFailure_masksAccountNumber() {
            when(codefBankTransferExchange.requestTransfer(any()))
                    .thenThrow(new RestClientException("read timeout"));

            assertThatThrownBy(() -> service.sendOneWon("004", "12345678901", "9999"))
                    .isInstanceOf(BusinessException.class);

            String logs = logMessages();
            assertThat(logs).doesNotContain("12345678901");
            assertThat(logs).contains("123456*8901");
        }
    }
}
