package com.team10.backend.domain.investment.client.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KisOrderbookMessageParserTest {

    private static final String REAL_ACK_MESSAGE = """
            {"header":{"tr_id":"H0STASP0","tr_key":"005930","encrypt":"N"},"body":{"rt_cd":"0","msg_cd":"OPSP0000","msg1":"SUBSCRIBE SUCCESS","output":{"iv":"4060f9fcb7b4e2ef","key":"tesnaipyhhcpfrnupdybjhvuabognuch"}}}
            """;

    private static final String REAL_ORDERBOOK_MESSAGE = "0|H0STASP0|001|"
            + "005930^145856^0^358500^359000^359500^360000^360500^361000^361500^362000^362500^363000"
            + "^358000^357500^357000^356500^356000^355500^355000^354500^354000^353500"
            + "^53949^91247^124706^311138^36136^50040^21635^48345^14697^44313"
            + "^40154^39078^38644^26290^25867^9410^17975^5281^14389^10406"
            + "^796206^227494^0^0^0^0^529267^-346500^5^-100.00^25970877^-979^95^0^0^0^358250^4^1";

    private static final String REAL_ORDERBOOK_MESSAGE_LATER = "0|H0STASP0|001|"
            + "005930^145907^0^358500^359000^359500^360000^360500^361000^361500^362000^362500^363000"
            + "^358000^357500^357000^356500^356000^355500^355000^354500^354000^353500"
            + "^48592^94055^124087^314258^36374^50074^21772^48161^14902^44404"
            + "^43438^53553^24344^26312^25770^9477^17840^5279^14374^10121"
            + "^796679^230508^0^0^0^0^529267^-346500^5^-100.00^25983370^260^0^0^0^0^358250^0^0";

    private final KisOrderbookMessageParser parser = new KisOrderbookMessageParser();

    @Test
    @DisplayName("실제 수신된 KIS 프레임 실시간 호가 메시지를 파싱한다")
    void parseFramedOrderbookMessage() {
        Optional<RealtimeOrderbookSnapshot> actual = parser.parse(REAL_ORDERBOOK_MESSAGE);

        assertThat(actual).isPresent();
        RealtimeOrderbookSnapshot snapshot = actual.get();
        assertThat(snapshot.stockCode()).isEqualTo("005930");
        assertThat(snapshot.businessTime()).isEqualTo("145856");
        assertThat(snapshot.timeType()).isEqualTo("0");
        assertThat(snapshot.totalAskQuantity()).isEqualTo(796206L);
        assertThat(snapshot.totalBidQuantity()).isEqualTo(227494L);

        assertThat(snapshot.asks()).hasSize(10);
        assertThat(snapshot.asks().getFirst().level()).isEqualTo(1);
        assertThat(snapshot.asks().getFirst().price()).isEqualTo(358500L);
        assertThat(snapshot.asks().getFirst().quantity()).isEqualTo(53949L);
        assertThat(snapshot.asks().getLast().level()).isEqualTo(10);
        assertThat(snapshot.asks().getLast().price()).isEqualTo(363000L);
        assertThat(snapshot.asks().getLast().quantity()).isEqualTo(44313L);

        assertThat(snapshot.bids()).hasSize(10);
        assertThat(snapshot.bids().getFirst().level()).isEqualTo(1);
        assertThat(snapshot.bids().getFirst().price()).isEqualTo(358000L);
        assertThat(snapshot.bids().getFirst().quantity()).isEqualTo(40154L);
        assertThat(snapshot.bids().getLast().level()).isEqualTo(10);
        assertThat(snapshot.bids().getLast().price()).isEqualTo(353500L);
        assertThat(snapshot.bids().getLast().quantity()).isEqualTo(10406L);
    }

    @Test
    @DisplayName("실제 수신된 후속 호가 메시지도 동일한 필드 위치로 파싱한다")
    void parseLaterRealOrderbookMessage() {
        Optional<RealtimeOrderbookSnapshot> actual = parser.parse(REAL_ORDERBOOK_MESSAGE_LATER);

        assertThat(actual).isPresent();
        RealtimeOrderbookSnapshot snapshot = actual.get();
        assertThat(snapshot.stockCode()).isEqualTo("005930");
        assertThat(snapshot.businessTime()).isEqualTo("145907");
        assertThat(snapshot.totalAskQuantity()).isEqualTo(796679L);
        assertThat(snapshot.totalBidQuantity()).isEqualTo(230508L);
        assertThat(snapshot.asks().getFirst().price()).isEqualTo(358500L);
        assertThat(snapshot.asks().getFirst().quantity()).isEqualTo(48592L);
        assertThat(snapshot.bids().getFirst().price()).isEqualTo(358000L);
        assertThat(snapshot.bids().getFirst().quantity()).isEqualTo(43438L);
    }

    @Test
    @DisplayName("실제 수신 메시지에서 프레임을 제거한 payload만 전달되어도 파싱한다")
    void parsePayloadOnlyMessage() {
        Optional<RealtimeOrderbookSnapshot> actual = parser.parse(payloadOnly(REAL_ORDERBOOK_MESSAGE));

        assertThat(actual).isPresent();
        assertThat(actual.get().stockCode()).isEqualTo("005930");
        assertThat(actual.get().businessTime()).isEqualTo("145856");
        assertThat(actual.get().asks()).hasSize(10);
        assertThat(actual.get().bids()).hasSize(10);
    }

    @Test
    @DisplayName("실제 수신된 JSON 구독 성공 ACK 메시지는 파싱 대상에서 제외한다")
    void ignoreJsonAckMessage() {
        Optional<RealtimeOrderbookSnapshot> actual = parser.parse(REAL_ACK_MESSAGE);

        assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("JSON PINGPONG 메시지는 파싱 대상에서 제외한다")
    void ignoreJsonPingPongMessage() {
        Optional<RealtimeOrderbookSnapshot> actual = parser.parse("{\"header\":{\"tr_id\":\"PINGPONG\"}}");

        assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("실시간 호가 TR ID가 아닌 프레임 메시지는 파싱 대상에서 제외한다")
    void ignoreOtherTrIdMessage() {
        String message = REAL_ORDERBOOK_MESSAGE.replace("0|H0STASP0|001|", "0|H0STCNT0|001|");

        Optional<RealtimeOrderbookSnapshot> actual = parser.parse(message);

        assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("필수 필드 개수가 부족한 메시지는 파싱 대상에서 제외한다")
    void ignoreInsufficientFieldsMessage() {
        String message = "005930^093730^0^71900";

        Optional<RealtimeOrderbookSnapshot> actual = parser.parse(message);

        assertThat(actual).isEmpty();
    }

    private String payloadOnly(String message) {
        return message.split("\\|", 4)[3];
    }
}
