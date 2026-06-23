package com.team10.backend.domain.investment.realtime.service.stream;

import com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookSseConstants;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 현재 서버 인스턴스에 직접 연결된 SSE 세션들을 관리하는 인메모리 레지스트리.
 *
 * <p>멀티 인스턴스 전체 구독 상태는 Redis가 기준이 되고, 이 클래스는 "내 인스턴스에 연결된 emitter"만 관리한다.
 * 즉 KIS 호가 데이터가 Redis Pub/Sub을 통해 모든 인스턴스에 전달되면, 각 인스턴스는 이 Registry를 조회해서 자기에게 연결된 클라이언트에게만 SSE 이벤트를 전송한다.
 */
@Slf4j
@Component
public class RealtimeOrderbookSseEmitterRegistry {

    private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);

    /**
     * streamId 기준 SSE 연결 정보.
     * <p>streamId는 브라우저 탭 하나의 실시간 호가 수신 세션을 식별한다.
     */
    private final Map<String, StreamRegistration> registrations = new ConcurrentHashMap<>();

    /**
     * 종목별 로컬 streamId 역방향 인덱스.
     * <p>특정 stockCode의 호가가 들어왔을 때 전체 stream을 훑지 않고 대상 emitter를 찾기 위해 유지한다.
     */
    private final Map<String, Set<String>> streamIdsByStockCode = new ConcurrentHashMap<>();

    private final Supplier<SseEmitter> emitterFactory;

    public RealtimeOrderbookSseEmitterRegistry() {
        this(() -> new SseEmitter(DEFAULT_TIMEOUT_MILLIS));
    }

    RealtimeOrderbookSseEmitterRegistry(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    /**
     * 한 탭의 SSE 연결을 등록한다.
     *
     * <p>정책상 하나의 streamId는 하나의 stockCode만 구독한다. 같은 사용자가 여러 탭을 열면 이 메서드가 여러 번
     * 호출되어 서로 다른 streamId가 생성될 수 있다.
     *
     * @param terminationCallback SSE 연결 종료 시 streamId를 기준으로 Redis 구독 상태를 정리하기 위해 호출할 콜백
     */
    public RealtimeOrderbookSseConnection register(
            Long userId,
            String stockCode,
            Consumer<String> terminationCallback
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (!StringUtils.hasText(stockCode)) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }

        String streamId = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.get();
        StreamRegistration registration = new StreamRegistration(
                streamId,
                userId,
                stockCode,
                emitter,
                terminationCallback == null ? ignored -> {
                } : terminationCallback
        );

        registrations.put(streamId, registration);
        streamIdsByStockCode
                .computeIfAbsent(stockCode, key -> ConcurrentHashMap.newKeySet())
                .add(streamId);

        /**
         * 브라우저 종료, 네트워크 단절, 서버 timeout 등으로 SSE가 끊길 수 있다.
         * 어떤 경로로 끊기더라도 로컬 인덱스와 Redis 구독 상태가 정리되어야 하므로 동일한 cleanup을 사용한다.
         *
         * cleanup 내부에서 AtomicBoolean으로 중복 실행을 막기 때문에 completion, timeout, error가 겹쳐도
         * terminationCallback은 한 번만 실행된다.
         */
        emitter.onCompletion(() -> cleanup(registration));
        emitter.onTimeout(() -> {
            cleanup(registration);
            emitter.complete();
        });
        emitter.onError(error -> cleanup(registration));

        return registration.toConnection();
    }

    /**
     * streamId가 현재 인스턴스에 연결된 stream인지 조회한다.
     */
    public Optional<RealtimeOrderbookSseConnection> find(String streamId) {
        return Optional.ofNullable(registrations.get(streamId))
                .map(StreamRegistration::toConnection);
    }

    /**
     * 특정 종목을 구독 중인 현재 인스턴스의 streamId 목록을 반환한다.
     * <p>내부 Set을 직접 노출하지 않기 위해 복사본을 반환한다.
     */
    public Set<String> findStreamIdsByStockCode(String stockCode) {
        Set<String> streamIds = streamIdsByStockCode.get(stockCode);
        if (streamIds == null) {
            return Set.of();
        }

        return Set.copyOf(streamIds);
    }

    /**
     * 현재 인스턴스에 연결된 전체 streamId 목록을 반환한다.
     * <p>Redis lease 갱신처럼 종목과 무관하게 모든 로컬 SSE stream을 순회해야 하는 작업에서 사용한다.
     */
    public Set<String> findAllStreamIds() {
        return Set.copyOf(registrations.keySet());
    }

    public int streamCount() {
        return registrations.size();
    }

    public int streamCountByStockCode(String stockCode) {
        return findStreamIdsByStockCode(stockCode).size();
    }

    /**
     * stream을 명시적으로 종료한다.
     *
     * <p>DELETE /streams/{streamId} 요청이 현재 인스턴스로 들어온 경우뿐 아니라, 다른 인스턴스에서 처리된 종료 요청을
     * Redis Pub/Sub 이벤트로 수신했을 때도 호출될 수 있다.
     */
    public boolean complete(String streamId) {
        StreamRegistration registration = registrations.get(streamId);
        if (registration == null) {
            return false;
        }

        cleanup(registration);
        registration.emitter().complete();
        return true;
    }

    /**
     * 특정 종목을 구독 중인 현재 인스턴스의 SSE stream들에게 최신 호가 데이터를 전송한다.
     *
     * <p>KIS WebSocket을 직접 수신하는 leader 인스턴스만 사용하는 메서드가 아니다. Redis orderbook-updated Pub/Sub을 수신한
     * 모든 인스턴스가 자기 로컬 emitter에게 전송하기 위해 이 메서드를 호출한다.
     *
     * @return 전송에 성공한 로컬 stream 수
     */
    public int sendOrderbookUpdateToSubscribers(String stockCode, String eventName, RealtimeOrderbookSnapshot data) {
        if (!StringUtils.hasText(eventName)) {
            throw new IllegalArgumentException("eventName must not be blank");
        }

        Set<String> streamIds = streamIdsByStockCode.getOrDefault(stockCode, Collections.emptySet());
        int sentCount = 0;

        for (String streamId : Set.copyOf(streamIds)) {
            StreamRegistration registration = registrations.get(streamId);
            if (registration == null) {
                continue;
            }

            try {
                registration.emitter().send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
                sentCount++;
            } catch (IOException | IllegalStateException e) {
                log.warn("Failed to send realtime orderbook SSE. streamId={}, stockCode={}",
                        streamId,
                        stockCode,
                        e);
                cleanup(registration);
                registration.emitter().completeWithError(e);
            }
        }

        return sentCount;
    }

    /**
     * 현재 인스턴스에 연결된 모든 SSE stream에 heartbeat comment를 전송한다.
     *
     * <p>SSE는 서버에서 클라이언트로만 흐르는 단방향 통신이므로 클라이언트 ACK를 직접 받을 수 없다.
     * 대신 주기적으로 작은 write를 시도해서 이미 끊어진 TCP 연결을 빠르게 감지한다.
     *
     * @return heartbeat 전송에 성공한 로컬 stream 수
     */
    public int sendHeartbeatToAll() {
        int sentCount = 0;

        for (String streamId : findAllStreamIds()) {
            StreamRegistration registration = registrations.get(streamId);
            if (registration == null) {
                continue;
            }

            try {
                registration.emitter().send(SseEmitter.event()
                        .comment(RealtimeOrderbookSseConstants.HEARTBEAT_COMMENT));
                sentCount++;
            } catch (IOException | IllegalStateException e) {
                log.warn("Failed to send realtime orderbook SSE heartbeat. streamId={}, stockCode={}",
                        streamId,
                        registration.stockCode(),
                        e);
                cleanup(registration);
                registration.emitter().completeWithError(e);
            }
        }

        return sentCount;
    }

    /**
     * stream 종료 시 로컬 상태를 제거하고 외부 정리 콜백을 실행한다.
     * <p>SseEmitter lifecycle callback과 명시적 complete 호출이 중복될 수 있으므로 AtomicBoolean으로 중복 정리를 막는다.
     */
    private void cleanup(StreamRegistration registration) {
        if (!registration.closed().compareAndSet(false, true)) {
            return;
        }

        registrations.remove(registration.streamId(), registration);
        removeFromStockIndex(registration.stockCode(), registration.streamId());
        try {
            registration.terminationCallback().accept(registration.streamId());
        } catch (RuntimeException e) {
            log.warn("Failed to run realtime orderbook SSE termination callback. streamId={}, stockCode={}",
                    registration.streamId(),
                    registration.stockCode(),
                    e);
        }
    }

    /**
     * 종목별 역방향 인덱스에서 streamId를 제거한다.
     * <p>해당 종목을 구독하는 로컬 stream이 더 이상 없으면 key 자체를 제거한다.
     */
    private void removeFromStockIndex(String stockCode, String streamId) {
        streamIdsByStockCode.computeIfPresent(stockCode, (key, streamIds) -> {
            streamIds.remove(streamId);
            return streamIds.isEmpty() ? null : streamIds;
        });
    }

    /**
     * Registry 내부에서만 사용하는 로컬 stream 등록 정보.
     * <p>외부에는 불변 DTO인 RealtimeOrderbookSseConnection 형태로만 노출한다.
     */
    private record StreamRegistration(
            String streamId,
            Long userId,
            String stockCode,
            SseEmitter emitter,
            Consumer<String> terminationCallback,
            AtomicBoolean closed
    ) {

        private StreamRegistration(
                String streamId,
                Long userId,
                String stockCode,
                SseEmitter emitter,
                Consumer<String> terminationCallback
        ) {
            this(streamId, userId, stockCode, emitter, terminationCallback, new AtomicBoolean(false));
        }

        private RealtimeOrderbookSseConnection toConnection() {
            return new RealtimeOrderbookSseConnection(streamId, userId, stockCode, emitter);
        }
    }
}
