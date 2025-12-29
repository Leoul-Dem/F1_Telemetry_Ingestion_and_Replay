package com.f1replay.service;

import com.f1replay.config.ReplayProperties;
import com.f1replay.config.ReplayProperties.SessionConfig;
import com.f1replay.model.SessionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ReplayProperties replayProperties;
    private final RedisStreamService redisStreamService;

    // Cache of session info (populated from config + Redis metadata)
    private final Map<String, SessionInfo> sessionCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSessionsFromConfig();
        log.info("Loaded {} sessions from configuration", sessionCache.size());
    }

    /**
     * Load sessions from application.properties config
     */
    private void loadSessionsFromConfig() {
        List<SessionConfig> sessions = replayProperties.getSessions();

        for (SessionConfig config : sessions) {
            try {
                SessionInfo info = buildSessionInfo(config);
                sessionCache.put(config.getKey(), info);
                log.debug("Loaded session: {} - {}", config.getKey(), config.getName());
            } catch (Exception e) {
                log.error("Failed to load session config for key {}: {}", config.getKey(), e.getMessage());
            }
        }
    }

    /**
     * Build SessionInfo from config, enriching with Redis stream metadata
     */
    private SessionInfo buildSessionInfo(SessionConfig config) {
        // Calculate duration from config times
        Long durationMs = null;
        try {
            Instant start = Instant.parse(config.getDateStart());
            Instant end = Instant.parse(config.getDateEnd());
            durationMs = Duration.between(start, end).toMillis();
        } catch (Exception e) {
            log.warn("Could not calculate duration for session {}", config.getKey());
        }

        // Get stream counts from Redis (may be null if stream doesn't exist yet)
        Integer locationCount = null;
        Integer carDataCount = null;

        try {
            String locationStreamKey = redisStreamService.getLocationStreamKey(config.getKey());
            String carDataStreamKey = redisStreamService.getCarDataStreamKey(config.getKey());

            Long locCount = redisStreamService.getStreamLength(locationStreamKey);
            Long carCount = redisStreamService.getStreamLength(carDataStreamKey);

            locationCount = locCount != null ? locCount.intValue() : null;
            carDataCount = carCount != null ? carCount.intValue() : null;
        } catch (Exception e) {
            log.warn("Could not get stream counts for session {}: {}", config.getKey(), e.getMessage());
        }

        return SessionInfo.builder()
                .sessionKey(config.getKey())
                .name(config.getName())
                .dateStart(config.getDateStart())
                .dateEnd(config.getDateEnd())
                .durationMs(durationMs)
                .locationCount(locationCount)
                .carDataCount(carDataCount)
                .build();
    }

    /**
     * Get all available sessions
     */
    public List<SessionInfo> getAllSessions() {
        return sessionCache.values().stream()
                .collect(Collectors.toList());
    }

    /**
     * Get a session by its key
     */
    public Optional<SessionInfo> getSession(String sessionKey) {
        return Optional.ofNullable(sessionCache.get(sessionKey));
    }

    /**
     * Check if a session exists
     */
    public boolean sessionExists(String sessionKey) {
        return sessionCache.containsKey(sessionKey);
    }

    /**
     * Refresh session metadata (stream counts) from Redis
     */
    public SessionInfo refreshSession(String sessionKey) {
        SessionConfig config = replayProperties.getSessions().stream()
                .filter(s -> s.getKey().equals(sessionKey))
                .findFirst()
                .orElse(null);

        if (config == null) {
            return null;
        }

        SessionInfo refreshedInfo = buildSessionInfo(config);
        sessionCache.put(sessionKey, refreshedInfo);
        return refreshedInfo;
    }

    /**
     * Check if session has data in Redis
     */
    public boolean sessionHasData(String sessionKey) {
        String locationStreamKey = redisStreamService.getLocationStreamKey(sessionKey);
        return redisStreamService.streamExists(locationStreamKey);
    }
}