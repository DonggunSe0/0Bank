package com.team10.backend.domain.investment.client.realtime;

import static com.team10.backend.domain.investment.config.KisConstants.Header.TR_ID;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.BODY;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.HEADER;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.INPUT;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.ORDERBOOK_TR_ID;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.SUBSCRIBE_TR_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.TR_KEY;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.TR_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.UNSUBSCRIBE_TR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.client.auth.KisWebSocketApprovalKeyManager;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookLevel;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import com.team10.backend.domain.investment.realtime.event.orderbookupdate.RealtimeOrderbookUpdatedEventPublisher;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

@ExtendWith(MockitoExtension.class)
class KisOrderbookWebSocketClientTest {

    private static final URI ENDPOINT_URI = URI.create("ws://localhost:21000");

    @Mock
    private WebSocketClient webSocketClient;

    @Mock
    private WebSocketSession session;

    @Mock
    private KisWebSocketApprovalKeyManager approvalKeyManager;

    @Mock
    private KisOrderbookMessageParser messageParser;

    @Mock
    private RealtimeOrderbookUpdatedEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<WebSocketHandler> handlerRef = new AtomicReference<>();
    private final List<TextMessage> sentMessages = new ArrayList<>();

    private KisOrderbookWebSocketClient client;

    @BeforeEach
    void setUp() {
        when(session.getId()).thenReturn("session-1");
        lenient().when(session.isOpen()).thenReturn(true);
        when(webSocketClient.execute(any(WebSocketHandler.class), any(WebSocketHttpHeaders.class), eq(ENDPOINT_URI)))
                .thenAnswer(invocation -> {
                    WebSocketHandler handler = invocation.getArgument(0);
                    handlerRef.set(handler);
                    handler.afterConnectionEstablished(session);
                    return CompletableFuture.completedFuture(session);
                });
        try {
            lenient().doAnswer(invocation -> {
                WebSocketMessage<?> message = invocation.getArgument(0);
                sentMessages.add((TextMessage) message);
                return null;
            }).when(session).sendMessage(any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        client = new KisOrderbookWebSocketClient(
                webSocketClient,
                approvalKeyManager,
                messageParser,
                eventPublisher,
                objectMapper,
                ENDPOINT_URI
        );
    }

    @Test
    @DisplayName("구독 요청 시 KIS WebSocket 연결을 생성하고 tr_type=1 메시지를 전송한다")
    void subscribe() throws Exception {
        when(approvalKeyManager.getApprovalKey()).thenReturn("approval-key");

        boolean subscribed = client.subscribe("005930");

        assertThat(subscribed).isTrue();
        assertThat(client.isConnected()).isTrue();
        assertThat(client.subscribedStockCodes()).containsExactly("005930");

        JsonNode message = sentMessage(0);
        assertThat(message.path(HEADER).path("approval_key").asText()).isEqualTo("approval-key");
        assertThat(message.path(HEADER).path(TR_TYPE).asText()).isEqualTo(SUBSCRIBE_TR_TYPE);
        assertThat(message.path(BODY).path(INPUT).path(TR_ID).asText()).isEqualTo(ORDERBOOK_TR_ID);
        assertThat(message.path(BODY).path(INPUT).path(TR_KEY).asText()).isEqualTo("005930");
    }

    @Test
    @DisplayName("이미 구독 중인 종목은 중복 구독 메시지를 전송하지 않는다")
    void ignoreDuplicateSubscribe() {
        when(approvalKeyManager.getApprovalKey()).thenReturn("approval-key");
        client.subscribe("005930");
        sentMessages.clear();

        boolean subscribed = client.subscribe("005930");

        assertThat(subscribed).isFalse();
        assertThat(sentMessages).isEmpty();
    }

    @Test
    @DisplayName("구독 해지 요청 시 tr_type=2 메시지를 전송한다")
    void unsubscribe() throws Exception {
        when(approvalKeyManager.getApprovalKey()).thenReturn("approval-key");
        client.subscribe("005930");
        sentMessages.clear();

        boolean unsubscribed = client.unsubscribe("005930");

        assertThat(unsubscribed).isTrue();
        assertThat(client.subscribedStockCodes()).isEmpty();

        JsonNode message = sentMessage(0);
        assertThat(message.path(HEADER).path(TR_TYPE).asText()).isEqualTo(UNSUBSCRIBE_TR_TYPE);
        assertThat(message.path(BODY).path(INPUT).path(TR_KEY).asText()).isEqualTo("005930");
    }

    @Test
    @DisplayName("KIS 실시간 호가 payload를 수신하면 파싱 후 호가 갱신 이벤트를 발행한다")
    void publishOrderbookUpdatedEventWhenOrderbookPayloadReceived() throws Exception {
        RealtimeOrderbookSnapshot snapshot = snapshot("005930");
        when(messageParser.parse("raw-orderbook-message")).thenReturn(Optional.of(snapshot));
        client.connect();

        handlerRef.get().handleMessage(session, new TextMessage("raw-orderbook-message"));

        verify(eventPublisher).publish(snapshot);
    }

    @Test
    @DisplayName("KIS PINGPONG 메시지를 수신하면 동일 payload를 다시 전송한다")
    void replyPingPongMessage() throws Exception {
        client.connect();
        String pingPongMessage = "{\"header\":{\"tr_id\":\"PINGPONG\"}}";

        handlerRef.get().handleMessage(session, new TextMessage(pingPongMessage));

        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.getFirst().getPayload()).isEqualTo(pingPongMessage);
        verify(messageParser, never()).parse(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("WebSocket 연결이 종료되면 세션과 구독 중인 종목 상태를 초기화한다")
    void clearStateWhenConnectionClosed() throws Exception {
        when(approvalKeyManager.getApprovalKey()).thenReturn("approval-key");
        client.subscribe("005930");

        handlerRef.get().afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(client.isConnected()).isFalse();
        assertThat(client.subscribedStockCodes()).isEmpty();
    }

    private JsonNode sentMessage(int index) throws Exception {
        return objectMapper.readTree(sentMessages.get(index).getPayload());
    }

    private RealtimeOrderbookSnapshot snapshot(String stockCode) {
        return new RealtimeOrderbookSnapshot(
                stockCode,
                "145856",
                "0",
                List.of(new RealtimeOrderbookLevel(1, 358500L, 53949L)),
                List.of(new RealtimeOrderbookLevel(1, 358000L, 40154L)),
                796206L,
                227494L
        );
    }
}
