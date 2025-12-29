package com.f1replay.websocket;

import com.f1replay.config.ReplayProperties;
import com.f1replay.model.PlaybackState;
import com.f1replay.model.PlaybackStatus;
import com.f1replay.model.TelemetryBatch;
import com.f1replay.service.ReplayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final ReplayService replayService;
    private final ReplayProperties replayProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Track active WebSocket sessions (wsSessionId -> ClientSession)
    private final Map<String, ClientSession> clientSessions = new ConcurrentHashMap<>();

    // Scheduler for sending telemetry batches
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionKey = extractSessionKey(session);
        if (sessionKey == null) {
            log.warn("WebSocket connection without session key, closing");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        ClientSession clientSession = new ClientSession(session, sessionKey);
        clientSessions.put(session.getId(), clientSession);

        log.info("WebSocket connected: {} for session {}", session.getId(), sessionKey);

        // Send initial state
        PlaybackState state = replayService.getState(sessionKey);
        if (state != null) {
            sendMessage(session, new WebSocketMessage("REPLAY_STATE", state));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ClientSession clientSession = clientSessions.get(session.getId());
        if (clientSession == null) {
            return;
        }

        try {
            WebSocketCommand command = objectMapper.readValue(message.getPayload(), WebSocketCommand.class);
            handleCommand(clientSession, command);
        } catch (Exception e) {
            log.error("Error processing WebSocket message: {}", e.getMessage());
            sendError(session, "Invalid message format");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ClientSession clientSession = clientSessions.remove(session.getId());
        if (clientSession != null) {
            // Cancel streaming task
            clientSession.cancelStreaming();

            // Notify replay service of disconnect
            replayService.onClientDisconnect(clientSession.sessionKey);

            log.info("WebSocket disconnected: {} (status: {})", session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    /**
     * Handle incoming commands from client
     */
    private void handleCommand(ClientSession clientSession, WebSocketCommand command) {
        String sessionKey = clientSession.sessionKey;
        WebSocketSession wsSession = clientSession.wsSession;

        try {
            switch (command.type().toUpperCase()) {
                case "SUBSCRIBE" -> {
                    // Start streaming telemetry
                    startStreaming(clientSession);
                    sendMessage(wsSession, new WebSocketMessage("SUBSCRIBED",
                            Map.of("sessionKey", sessionKey)));
                }
                case "UNSUBSCRIBE" -> {
                    // Stop streaming
                    clientSession.cancelStreaming();
                    sendMessage(wsSession, new WebSocketMessage("UNSUBSCRIBED", null));
                }
                case "PLAY" -> {
                    String startTime = command.data() != null ?
                            (String) command.data().get("startTime") : null;
                    PlaybackState state = replayService.play(sessionKey, startTime);
                    sendMessage(wsSession, new WebSocketMessage("REPLAY_STATE", state));
                    startStreaming(clientSession);
                }
                case "PAUSE" -> {
                    PlaybackState state = replayService.pause(sessionKey);
                    clientSession.cancelStreaming();
                    sendMessage(wsSession, new WebSocketMessage("REPLAY_STATE", state));
                }
                case "STOP" -> {
                    PlaybackState state = replayService.stop(sessionKey);
                    clientSession.cancelStreaming();
                    sendMessage(wsSession, new WebSocketMessage("REPLAY_STATE", state));
                }
                case "SEEK" -> {
                    String targetTime = (String) command.data().get("targetTime");
                    PlaybackState state = replayService.seek(sessionKey, targetTime);
                    sendMessage(wsSession, new WebSocketMessage("REPLAY_STATE", state));
                }
                case "SPEED" -> {
                    double speed = ((Number) command.data().get("speed")).doubleValue();
                    var playbackSpeed = com.f1replay.model.PlaybackSpeed.fromMultiplier(speed);
                    PlaybackState state = replayService.setSpeed(sessionKey, playbackSpeed);
                    sendMessage(wsSession, new WebSocketMessage("REPLAY_STATE", state));
                }
                case "GET_STATE" -> {
                    PlaybackState state = replayService.getState(sessionKey);
                    sendMessage(wsSession, new WebSocketMessage("REPLAY_STATE", state));
                }
                default -> {
                    log.warn("Unknown command type: {}", command.type());
                    sendError(wsSession, "Unknown command: " + command.type());
                }
            }
        } catch (IllegalArgumentException e) {
            sendError(wsSession, e.getMessage());
        } catch (Exception e) {
            log.error("Error handling command {}: {}", command.type(), e.getMessage());
            sendError(wsSession, "Internal error processing command");
        }
    }

    /**
     * Start streaming telemetry data to client
     */
    private void startStreaming(ClientSession clientSession) {
        // Cancel existing streaming if any
        clientSession.cancelStreaming();

        long intervalMs = replayProperties.getBatch().getIntervalMs();

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> streamTelemetryBatch(clientSession),
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        clientSession.setStreamingTask(task);
        log.debug("Started streaming for session {}", clientSession.sessionKey);
    }

    /**
     * Stream a single telemetry batch to client
     */
    private void streamTelemetryBatch(ClientSession clientSession) {
        if (!clientSession.wsSession.isOpen()) {
            clientSession.cancelStreaming();
            return;
        }

        try {
            // Check if replay is playing
            PlaybackState state = replayService.getState(clientSession.sessionKey);
            if (state == null || state.getStatus() != PlaybackStatus.PLAYING) {
                return;
            }

            // Get next batch
            TelemetryBatch batch = replayService.getNextBatch(clientSession.sessionKey);
            if (batch != null) {
                sendMessage(clientSession.wsSession, new WebSocketMessage("TELEMETRY_BATCH", batch));
            } else {
                // Playback ended
                state = replayService.getState(clientSession.sessionKey);
                if (state != null && state.getStatus() == PlaybackStatus.STOPPED) {
                    sendMessage(clientSession.wsSession, new WebSocketMessage("PLAYBACK_COMPLETE", null));
                    clientSession.cancelStreaming();
                }
            }
        } catch (Exception e) {
            log.error("Error streaming telemetry: {}", e.getMessage());
        }
    }

    /**
     * Extract session key from WebSocket URL path
     */
    private String extractSessionKey(WebSocketSession session) {
        String path = session.getUri().getPath();
        // Path format: /ws/telemetry/{sessionKey}
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return null;
    }

    /**
     * Send a message to a WebSocket session
     */
    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Error sending WebSocket message: {}", e.getMessage());
        }
    }

    /**
     * Send an error message to a WebSocket session
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        sendMessage(session, new WebSocketMessage("ERROR", Map.of("message", errorMessage)));
    }

    // ============ Inner Classes ============

    /**
     * Tracks a connected WebSocket client
     */
    private static class ClientSession {
        final WebSocketSession wsSession;
        final String sessionKey;
        volatile ScheduledFuture<?> streamingTask;

        ClientSession(WebSocketSession wsSession, String sessionKey) {
            this.wsSession = wsSession;
            this.sessionKey = sessionKey;
        }

        void setStreamingTask(ScheduledFuture<?> task) {
            this.streamingTask = task;
        }

        void cancelStreaming() {
            if (streamingTask != null && !streamingTask.isCancelled()) {
                streamingTask.cancel(false);
                streamingTask = null;
            }
        }
    }

    /**
     * WebSocket message format (outgoing)
     */
    public record WebSocketMessage(String type, Object data) {}

    /**
     * WebSocket command format (incoming)
     */
    public record WebSocketCommand(String type, Map<String, Object> data) {}
}