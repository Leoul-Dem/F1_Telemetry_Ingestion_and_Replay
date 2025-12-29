package com.f1replay.service;

import com.f1replay.config.ReplayProperties;
import com.f1replay.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class ReplayService {

    private final RedisStreamService redisStreamService;
    private final SessionService sessionService;
    private final ReplayProperties replayProperties;

    // Active replay sessions (sessionKey -> ReplaySession)
    private final Map<String, ReplaySession> activeSessions = new ConcurrentHashMap<>();

    // Disconnected session states for reconnection (sessionKey -> DisconnectedState)
    private final Map<String, DisconnectedState> disconnectedStates = new ConcurrentHashMap<>();

    // Scheduler for replay timing
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Cleanup scheduler for expired states
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    public ReplayService(RedisStreamService redisStreamService,
                         SessionService sessionService,
                         ReplayProperties replayProperties) {
        this.redisStreamService = redisStreamService;
        this.sessionService = sessionService;
        this.replayProperties = replayProperties;

        // Start cleanup task for expired disconnected states
        cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredStates,
                1, 1, TimeUnit.MINUTES
        );
    }

    /**
     * Start or resume playback for a session
     */
    public PlaybackState play(String sessionKey, String startTime) {
        // Validate session exists
        if (!sessionService.sessionExists(sessionKey)) {
            throw new IllegalArgumentException("Session not found: " + sessionKey);
        }

        ReplaySession session = activeSessions.get(sessionKey);

        if (session == null) {
            // Check for disconnected state to resume
            DisconnectedState disconnected = disconnectedStates.remove(sessionKey);
            if (disconnected != null && startTime == null) {
                // Resume from disconnected state
                session = createReplaySession(sessionKey, disconnected.currentTime, disconnected.speed);
                log.info("Resuming session {} from disconnected state at {}", sessionKey, disconnected.currentTime);
            } else {
                // Start new session
                String effectiveStartTime = startTime;
                if (effectiveStartTime == null) {
                    effectiveStartTime = sessionService.getSession(sessionKey)
                            .map(SessionInfo::getDateStart)
                            .orElseThrow(() -> new IllegalArgumentException("Cannot determine start time"));
                }
                session = createReplaySession(sessionKey, effectiveStartTime, PlaybackSpeed.NORMAL);
                log.info("Starting new session {} from {}", sessionKey, effectiveStartTime);
            }
            activeSessions.put(sessionKey, session);
        }

        session.setStatus(PlaybackStatus.PLAYING);
        schedulePlayback(session);

        return buildPlaybackState(session);
    }

    /**
     * Pause playback for a session
     */
    public PlaybackState pause(String sessionKey) {
        ReplaySession session = activeSessions.get(sessionKey);
        if (session == null) {
            throw new IllegalArgumentException("No active session: " + sessionKey);
        }

        session.setStatus(PlaybackStatus.PAUSED);
        cancelScheduledTask(session);

        log.info("Paused session {} at {}", sessionKey, session.getCurrentTime());
        return buildPlaybackState(session);
    }

    /**
     * Stop playback and remove session
     */
    public PlaybackState stop(String sessionKey) {
        ReplaySession session = activeSessions.remove(sessionKey);
        if (session == null) {
            throw new IllegalArgumentException("No active session: " + sessionKey);
        }

        session.setStatus(PlaybackStatus.STOPPED);
        cancelScheduledTask(session);

        log.info("Stopped session {}", sessionKey);
        return buildPlaybackState(session);
    }

    /**
     * Seek to a specific timestamp
     */
    public PlaybackState seek(String sessionKey, String targetTime) {
        ReplaySession session = activeSessions.get(sessionKey);
        if (session == null) {
            throw new IllegalArgumentException("No active session: " + sessionKey);
        }

        // Validate target time is within session bounds
        SessionInfo info = sessionService.getSession(sessionKey).orElseThrow();
        Instant target = Instant.parse(targetTime);
        Instant start = Instant.parse(info.getDateStart());
        Instant end = Instant.parse(info.getDateEnd());

        if (target.isBefore(start) || target.isAfter(end)) {
            throw new IllegalArgumentException("Target time outside session bounds");
        }

        // Update position and clear buffer
        session.setCurrentTime(targetTime);
        session.clearBuffer();

        // Pre-load buffer at new position
        loadBuffer(session);

        log.info("Seeked session {} to {}", sessionKey, targetTime);
        return buildPlaybackState(session);
    }

    /**
     * Set playback speed
     */
    public PlaybackState setSpeed(String sessionKey, PlaybackSpeed speed) {
        ReplaySession session = activeSessions.get(sessionKey);
        if (session == null) {
            throw new IllegalArgumentException("No active session: " + sessionKey);
        }

        PlaybackSpeed oldSpeed = session.getSpeed();
        session.setSpeed(speed);

        // Reschedule if playing
        if (session.getStatus() == PlaybackStatus.PLAYING) {
            cancelScheduledTask(session);
            schedulePlayback(session);
        }

        log.info("Changed speed for session {} from {} to {}", sessionKey, oldSpeed, speed);
        return buildPlaybackState(session);
    }

    /**
     * Get current playback state
     */
    public PlaybackState getState(String sessionKey) {
        ReplaySession session = activeSessions.get(sessionKey);
        if (session == null) {
            // Check for disconnected state
            DisconnectedState disconnected = disconnectedStates.get(sessionKey);
            if (disconnected != null) {
                return PlaybackState.builder()
                        .sessionKey(sessionKey)
                        .status(PlaybackStatus.PAUSED)
                        .currentTime(disconnected.currentTime)
                        .speed(disconnected.speed)
                        .build();
            }
            return null;
        }
        return buildPlaybackState(session);
    }

    /**
     * Get next batch of telemetry data for streaming
     */
    public TelemetryBatch getNextBatch(String sessionKey) {
        ReplaySession session = activeSessions.get(sessionKey);
        if (session == null || session.getStatus() != PlaybackStatus.PLAYING) {
            return null;
        }

        // Get current batch interval worth of data
        String currentTime = session.getCurrentTime();
        long batchIntervalMs = replayProperties.getBatch().getIntervalMs();
        double speedMultiplier = session.getSpeed().getMultiplier();

        // Calculate time window for this batch (adjusted by speed)
        long effectiveIntervalMs = (long) (batchIntervalMs * speedMultiplier);
        Instant current = Instant.parse(currentTime);
        Instant batchEnd = current.plusMillis(effectiveIntervalMs);

        // Check if we've reached the end
        SessionInfo info = sessionService.getSession(sessionKey).orElse(null);
        if (info != null) {
            Instant sessionEnd = Instant.parse(info.getDateEnd());
            if (current.isAfter(sessionEnd) || current.equals(sessionEnd)) {
                session.setStatus(PlaybackStatus.STOPPED);
                return null;
            }
            if (batchEnd.isAfter(sessionEnd)) {
                batchEnd = sessionEnd;
            }
        }

        // Get data from buffer or fetch from Redis
        List<LocationPoint> locations = session.getLocationsInRange(currentTime, batchEnd.toString());
        List<CarData> carData = session.getCarDataInRange(currentTime, batchEnd.toString());

        // If buffer is low, trigger background refill
        if (session.getBufferRemainingMs() < 10000) { // Less than 10 seconds
            CompletableFuture.runAsync(() -> loadBuffer(session));
        }

        // Advance current time
        session.setCurrentTime(batchEnd.toString());

        return TelemetryBatch.builder()
                .batchTimestamp(currentTime)
                .locations(locations)
                .carData(carData)
                .build();
    }

    /**
     * Handle client disconnect - preserve state for reconnection
     */
    public void onClientDisconnect(String sessionKey) {
        ReplaySession session = activeSessions.remove(sessionKey);
        if (session != null) {
            cancelScheduledTask(session);

            // Store state for potential reconnection
            disconnectedStates.put(sessionKey, new DisconnectedState(
                    session.getCurrentTime(),
                    session.getSpeed(),
                    Instant.now()
            ));

            log.info("Client disconnected from session {}, state preserved for {} minutes",
                    sessionKey, replayProperties.getStateRetentionMinutes());
        }
    }

    /**
     * Check if a session has active replay
     */
    public boolean isSessionActive(String sessionKey) {
        return activeSessions.containsKey(sessionKey);
    }

    // ============ Internal Methods ============

    private ReplaySession createReplaySession(String sessionKey, String startTime, PlaybackSpeed speed) {
        SessionInfo info = sessionService.getSession(sessionKey).orElseThrow();

        ReplaySession session = new ReplaySession(
                sessionKey,
                startTime,
                info.getDateStart(),
                info.getDateEnd(),
                speed
        );

        // Pre-load initial buffer
        loadBuffer(session);

        return session;
    }

    private void loadBuffer(ReplaySession session) {
        int bufferSeconds = replayProperties.getBuffer().getDurationSeconds();
        String currentTime = session.getCurrentTime();
        Instant current = Instant.parse(currentTime);
        Instant bufferEnd = current.plusSeconds(bufferSeconds);

        // Don't exceed session end
        Instant sessionEnd = Instant.parse(session.getEndTime());
        if (bufferEnd.isAfter(sessionEnd)) {
            bufferEnd = sessionEnd;
        }

        String bufferEndTime = bufferEnd.toString();

        // Fetch data from Redis
        List<LocationPoint> locations = redisStreamService.readLocationsByTimeRange(
                session.getSessionKey(), currentTime, bufferEndTime);
        List<CarData> carData = redisStreamService.readCarDataByTimeRange(
                session.getSessionKey(), currentTime, bufferEndTime);

        session.addToBuffer(locations, carData, bufferEndTime);

        log.debug("Loaded buffer for session {}: {} locations, {} car data points, until {}",
                session.getSessionKey(), locations.size(), carData.size(), bufferEndTime);
    }

    private void schedulePlayback(ReplaySession session) {
        long intervalMs = replayProperties.getBatch().getIntervalMs();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> advancePlayback(session),
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        session.setScheduledTask(future);
    }

    private void advancePlayback(ReplaySession session) {
        if (session.getStatus() != PlaybackStatus.PLAYING) {
            return;
        }

        // This method is called by scheduler, the actual batch retrieval
        // is handled by WebSocket handler calling getNextBatch()

        // Check if we've reached the end
        SessionInfo info = sessionService.getSession(session.getSessionKey()).orElse(null);
        if (info != null) {
            Instant current = Instant.parse(session.getCurrentTime());
            Instant end = Instant.parse(info.getDateEnd());
            if (current.isAfter(end) || current.equals(end)) {
                session.setStatus(PlaybackStatus.STOPPED);
                cancelScheduledTask(session);
                log.info("Session {} playback completed", session.getSessionKey());
            }
        }
    }

    private void cancelScheduledTask(ReplaySession session) {
        ScheduledFuture<?> task = session.getScheduledTask();
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    private PlaybackState buildPlaybackState(ReplaySession session) {
        SessionInfo info = sessionService.getSession(session.getSessionKey()).orElse(null);

        long elapsedMs = 0;
        long durationMs = 0;

        if (info != null) {
            try {
                Instant start = Instant.parse(info.getDateStart());
                Instant end = Instant.parse(info.getDateEnd());
                Instant current = Instant.parse(session.getCurrentTime());

                durationMs = Duration.between(start, end).toMillis();
                elapsedMs = Duration.between(start, current).toMillis();
            } catch (Exception e) {
                log.warn("Error calculating playback times: {}", e.getMessage());
            }
        }

        return PlaybackState.builder()
                .sessionKey(session.getSessionKey())
                .status(session.getStatus())
                .currentTime(session.getCurrentTime())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .speed(session.getSpeed())
                .durationMs(durationMs)
                .elapsedMs(elapsedMs)
                .build();
    }

    private void cleanupExpiredStates() {
        int retentionMinutes = replayProperties.getStateRetentionMinutes();
        Instant cutoff = Instant.now().minusSeconds(retentionMinutes * 60L);

        disconnectedStates.entrySet().removeIf(entry -> {
            if (entry.getValue().disconnectedAt.isBefore(cutoff)) {
                log.debug("Cleaning up expired state for session {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    // ============ Inner Classes ============

    /**
     * Represents an active replay session
     */
    private static class ReplaySession {
        private final String sessionKey;
        private volatile String currentTime;
        private final String startTime;
        private final String endTime;
        private volatile PlaybackSpeed speed;
        private volatile PlaybackStatus status;
        private volatile ScheduledFuture<?> scheduledTask;

        // Buffer for pre-loaded data
        private final List<LocationPoint> locationBuffer = Collections.synchronizedList(new ArrayList<>());
        private final List<CarData> carDataBuffer = Collections.synchronizedList(new ArrayList<>());
        private volatile String bufferEndTime;

        public ReplaySession(String sessionKey, String currentTime, String startTime,
                             String endTime, PlaybackSpeed speed) {
            this.sessionKey = sessionKey;
            this.currentTime = currentTime;
            this.startTime = startTime;
            this.endTime = endTime;
            this.speed = speed;
            this.status = PlaybackStatus.STOPPED;
        }

        // Getters and setters
        public String getSessionKey() { return sessionKey; }
        public String getCurrentTime() { return currentTime; }
        public void setCurrentTime(String time) { this.currentTime = time; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public PlaybackSpeed getSpeed() { return speed; }
        public void setSpeed(PlaybackSpeed speed) { this.speed = speed; }
        public PlaybackStatus getStatus() { return status; }
        public void setStatus(PlaybackStatus status) { this.status = status; }
        public ScheduledFuture<?> getScheduledTask() { return scheduledTask; }
        public void setScheduledTask(ScheduledFuture<?> task) { this.scheduledTask = task; }

        public void clearBuffer() {
            locationBuffer.clear();
            carDataBuffer.clear();
            bufferEndTime = null;
        }

        public void addToBuffer(List<LocationPoint> locations, List<CarData> carData, String endTime) {
            locationBuffer.addAll(locations);
            carDataBuffer.addAll(carData);
            this.bufferEndTime = endTime;
        }

        public List<LocationPoint> getLocationsInRange(String start, String end) {
            Instant startInstant = Instant.parse(start);
            Instant endInstant = Instant.parse(end);

            List<LocationPoint> result = new ArrayList<>();
            synchronized (locationBuffer) {
                Iterator<LocationPoint> it = locationBuffer.iterator();
                while (it.hasNext()) {
                    LocationPoint point = it.next();
                    Instant pointTime = Instant.parse(point.getTimestamp());
                    if (!pointTime.isBefore(startInstant) && pointTime.isBefore(endInstant)) {
                        result.add(point);
                        it.remove(); // Remove consumed data from buffer
                    }
                }
            }
            return result;
        }

        public List<CarData> getCarDataInRange(String start, String end) {
            Instant startInstant = Instant.parse(start);
            Instant endInstant = Instant.parse(end);

            List<CarData> result = new ArrayList<>();
            synchronized (carDataBuffer) {
                Iterator<CarData> it = carDataBuffer.iterator();
                while (it.hasNext()) {
                    CarData data = it.next();
                    Instant dataTime = Instant.parse(data.getTimestamp());
                    if (!dataTime.isBefore(startInstant) && dataTime.isBefore(endInstant)) {
                        result.add(data);
                        it.remove(); // Remove consumed data from buffer
                    }
                }
            }
            return result;
        }

        public long getBufferRemainingMs() {
            if (bufferEndTime == null || currentTime == null) {
                return 0;
            }
            try {
                Instant current = Instant.parse(currentTime);
                Instant bufferEnd = Instant.parse(bufferEndTime);
                return Duration.between(current, bufferEnd).toMillis();
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /**
     * State preserved for disconnected clients
     */
    private record DisconnectedState(String currentTime, PlaybackSpeed speed, Instant disconnectedAt) {}
}