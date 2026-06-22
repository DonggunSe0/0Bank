package com.team10.backend.domain.investment.client.realtime;

import static com.team10.backend.domain.investment.config.KisConstants.Header.CONTENT_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.Header.CUST_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.Header.TR_ID;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.APPROVAL_KEY;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.BODY;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.HEADER;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.INPUT;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.ORDERBOOK_TR_ID;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.PERSONAL_CUST_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.PINGPONG_TR_ID;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.SUBSCRIBE_TR_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.TR_KEY;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.TR_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.UNSUBSCRIBE_TR_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.UTF_8_CONTENT_TYPE;
import static com.team10.backend.domain.investment.config.KisConstants.WEB_SOCKET_URL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.client.auth.KisWebSocketApprovalKeyManager;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import com.team10.backend.domain.investment.realtime.event.orderbookupdate.RealtimeOrderbookUpdatedEventPublisher;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class KisOrderbookWebSocketClient {

    private final WebSocketClient webSocketClient;
    private final KisWebSocketApprovalKeyManager approvalKeyManager;
    private final KisOrderbookMessageParser messageParser;
    private final RealtimeOrderbookUpdatedEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final URI endpointUri;

    private final Set<String> subscribedStockCodes = ConcurrentHashMap.newKeySet();
    private final Object connectionMonitor = new Object();
    private final Object subscriptionMonitor = new Object();

    private volatile WebSocketSession session;

    @Autowired
    public KisOrderbookWebSocketClient(
            KisWebSocketApprovalKeyManager approvalKeyManager,
            KisOrderbookMessageParser messageParser,
            RealtimeOrderbookUpdatedEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this(
                new StandardWebSocketClient(),
                approvalKeyManager,
                messageParser,
                eventPublisher,
                objectMapper,
                URI.create(WEB_SOCKET_URL)
        );
    }

    KisOrderbookWebSocketClient(
            WebSocketClient webSocketClient,
            KisWebSocketApprovalKeyManager approvalKeyManager,
            KisOrderbookMessageParser messageParser,
            RealtimeOrderbookUpdatedEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            URI endpointUri
    ) {
        this.webSocketClient = Objects.requireNonNull(webSocketClient, "webSocketClient must not be null");
        this.approvalKeyManager = Objects.requireNonNull(approvalKeyManager, "approvalKeyManager must not be null");
        this.messageParser = Objects.requireNonNull(messageParser, "messageParser must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.endpointUri = Objects.requireNonNull(endpointUri, "endpointUri must not be null");
    }

    public void connect() {
        if (isConnected()) {
            return;
        }

        synchronized (connectionMonitor) {
            if (isConnected()) {
                return;
            }

            try {
                WebSocketSession connectedSession = webSocketClient.execute(
                        new KisOrderbookWebSocketHandler(),
                        new WebSocketHttpHeaders(),
                        endpointUri
                ).join();
                this.session = connectedSession;
                log.info("KIS orderbook WebSocket connected. sessionId={}", connectedSession.getId());
            } catch (CompletionException e) {
                throw new IllegalStateException("KIS 실시간 호가 WebSocket 연결에 실패했습니다.", e.getCause());
            } catch (RuntimeException e) {
                throw new IllegalStateException("KIS 실시간 호가 WebSocket 연결에 실패했습니다.", e);
            }
        }
    }

    public boolean subscribe(String stockCode) {
        validateStockCode(stockCode);

        synchronized (subscriptionMonitor) {
            if (subscribedStockCodes.contains(stockCode)) {
                return false;
            }

            WebSocketSession openSession = getOpenSession();
            sendSubscriptionMessage(openSession, stockCode, SUBSCRIBE_TR_TYPE);
            subscribedStockCodes.add(stockCode);
            return true;
        }
    }

    public boolean unsubscribe(String stockCode) {
        validateStockCode(stockCode);

        synchronized (subscriptionMonitor) {
            if (!subscribedStockCodes.remove(stockCode)) {
                return false;
            }

            WebSocketSession currentSession = session;
            if (currentSession == null || !currentSession.isOpen()) {
                return true;
            }

            try {
                sendSubscriptionMessage(currentSession, stockCode, UNSUBSCRIBE_TR_TYPE);
                return true;
            } catch (RuntimeException e) {
                subscribedStockCodes.add(stockCode);
                throw e;
            }
        }
    }

    public void disconnect() {
        WebSocketSession currentSession = session;
        session = null;
        clearSubscribedStockCodes();

        if (currentSession == null || !currentSession.isOpen()) {
            return;
        }

        try {
            currentSession.close();
        } catch (IOException e) {
            log.warn("Failed to close KIS orderbook WebSocket session. sessionId={}", currentSession.getId(), e);
        }
    }

    public boolean isConnected() {
        WebSocketSession currentSession = session;
        return currentSession != null && currentSession.isOpen();
    }

    public Set<String> subscribedStockCodes() {
        return Set.copyOf(subscribedStockCodes);
    }

    private WebSocketSession getOpenSession() {
        connect();

        WebSocketSession currentSession = session;
        if (currentSession == null || !currentSession.isOpen()) {
            throw new IllegalStateException("KIS 실시간 호가 WebSocket 세션이 열려있지 않습니다.");
        }

        return currentSession;
    }

    private void sendSubscriptionMessage(WebSocketSession currentSession, String stockCode, String trType) {
        try {
            currentSession.sendMessage(new TextMessage(subscriptionMessage(stockCode, trType)));
        } catch (IOException e) {
            throw new IllegalStateException("KIS 실시간 호가 구독 메시지 전송에 실패했습니다.", e);
        }
    }

    private String subscriptionMessage(String stockCode, String trType) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                HEADER, Map.of(
                        APPROVAL_KEY, approvalKeyManager.getApprovalKey(),
                        CUST_TYPE, PERSONAL_CUST_TYPE,
                        TR_TYPE, trType,
                        CONTENT_TYPE, UTF_8_CONTENT_TYPE
                ),
                BODY, Map.of(
                        INPUT, Map.of(
                                TR_ID, ORDERBOOK_TR_ID,
                                TR_KEY, stockCode
                        )
                )
        ));
    }

    private void handleTextPayload(WebSocketSession currentSession, String payload) {
        if (!StringUtils.hasText(payload)) {
            return;
        }

        String trimmedPayload = payload.trim();
        if (isJson(trimmedPayload)) {
            handleJsonPayload(currentSession, trimmedPayload);
            return;
        }

        try {
            messageParser.parse(trimmedPayload)
                    .ifPresent(this::publishOrderbookSnapshot);
        } catch (RuntimeException e) {
            log.warn("Failed to parse KIS orderbook WebSocket message. payload={}", trimmedPayload, e);
        }
    }

    private void handleJsonPayload(WebSocketSession currentSession, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String trId = root.path(HEADER).path(TR_ID).asText();

            if (PINGPONG_TR_ID.equals(trId)) {
                currentSession.sendMessage(new TextMessage(payload));
                return;
            }

            if (ORDERBOOK_TR_ID.equals(trId)) {
                JsonNode body = root.path(BODY);
                log.debug("KIS orderbook WebSocket control message received. trId={}, stockCode={}, rtCd={}, msg={}",
                        trId,
                        root.path(HEADER).path(TR_KEY).asText(),
                        body.path("rt_cd").asText(),
                        body.path("msg1").asText());
            }
        } catch (IOException e) {
            log.warn("Failed to handle KIS orderbook WebSocket JSON message. payload={}", payload, e);
        }
    }

    private void publishOrderbookSnapshot(RealtimeOrderbookSnapshot snapshot) {
        eventPublisher.publish(snapshot);
    }

    private boolean isJson(String payload) {
        return payload.startsWith("{") || payload.startsWith("[");
    }

    private void clearSubscribedStockCodes() {
        synchronized (subscriptionMonitor) {
            subscribedStockCodes.clear();
        }
    }

    private void validateStockCode(String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
    }

    private class KisOrderbookWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession connectedSession) {
            session = connectedSession;
        }

        @Override
        protected void handleTextMessage(WebSocketSession currentSession, TextMessage message) {
            handleTextPayload(currentSession, message.getPayload());
        }

        @Override
        public void handleTransportError(WebSocketSession currentSession, Throwable exception) throws Exception {
            log.warn("KIS orderbook WebSocket transport error. sessionId={}", currentSession.getId(), exception);
            if (currentSession.isOpen()) {
                currentSession.close();
            }
            clearSession(currentSession);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession closedSession, CloseStatus status) {
            log.info("KIS orderbook WebSocket closed. sessionId={}, status={}", closedSession.getId(), status);
            clearSession(closedSession);
        }

        private void clearSession(WebSocketSession targetSession) {
            if (session == targetSession) {
                session = null;
                clearSubscribedStockCodes();
            }
        }
    }
}
