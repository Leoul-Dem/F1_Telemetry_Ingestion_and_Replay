package com.f1replay.service;

import com.f1replay.config.ReplayProperties;
import com.f1replay.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplayServiceTest {

    @Mock
    private RedisStreamService redisStreamService;

    @Mock
    private SessionService sessionService;

    @Mock
    private ReplayProperties replayProperties;

    @Mock
    private ReplayProperties.Batch batchProperties;

    @Mock
    private ReplayProperties.Buffer bufferProperties;

    private ReplayService replayService;

    private SessionInfo testSession;

    @BeforeEach
    void setUp() {
        // Setup test session
        testSession = SessionInfo.builder()
                .sessionKey("9140")
                .name("Monaco Grand Prix 2024 - Race")
                .dateStart("2024-05-12T14:00:00Z")
                .dateEnd("2024-05-12T16:00:00Z")
                .durationMs(7200000L)
                .build();

        // Setup properties mocks
        when(replayProperties.getBatch()).thenReturn(batchProperties);
        when(replayProperties.getBuffer()).thenReturn(bufferProperties);
        when(batchProperties.getIntervalMs()).thenReturn(100);
        when(bufferProperties.getDurationSeconds()).thenReturn(30);
        when(replayProperties.getStateRetentionMinutes()).thenReturn(5);

        replayService = new ReplayService(redisStreamService, sessionService, replayProperties);
    }

    @Test
    @DisplayName("play throws exception for non-existent session")
    void testPlayNonExistentSession() {
        when(sessionService.sessionExists("invalid")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                replayService.play("invalid", null));
    }

    @Test
    @DisplayName("play starts new session")
    void testPlayStartsNewSession() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        PlaybackState state = replayService.play("9140", "2024-05-12T14:00:00Z");

        assertNotNull(state);
        assertEquals("9140", state.getSessionKey());
        assertEquals(PlaybackStatus.PLAYING, state.getStatus());
        assertEquals("2024-05-12T14:00:00Z", state.getCurrentTime());
    }

    @Test
    @DisplayName("pause throws exception when no active session")
    void testPauseNoActiveSession() {
        assertThrows(IllegalArgumentException.class, () ->
                replayService.pause("9140"));
    }

    @Test
    @DisplayName("pause pauses active session")
    void testPausePausesSession() {
        // First start a session
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        // Then pause it
        PlaybackState state = replayService.pause("9140");

        assertEquals(PlaybackStatus.PAUSED, state.getStatus());
    }

    @Test
    @DisplayName("stop throws exception when no active session")
    void testStopNoActiveSession() {
        assertThrows(IllegalArgumentException.class, () ->
                replayService.stop("9140"));
    }

    @Test
    @DisplayName("stop stops active session")
    void testStopStopsSession() {
        // First start a session
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        // Then stop it
        PlaybackState state = replayService.stop("9140");

        assertEquals(PlaybackStatus.STOPPED, state.getStatus());
    }

    @Test
    @DisplayName("seek throws exception when no active session")
    void testSeekNoActiveSession() {
        assertThrows(IllegalArgumentException.class, () ->
                replayService.seek("9140", "2024-05-12T14:30:00Z"));
    }

    @Test
    @DisplayName("seek throws exception for time outside session bounds")
    void testSeekOutOfBounds() {
        // First start a session
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        // Try to seek outside bounds
        assertThrows(IllegalArgumentException.class, () ->
                replayService.seek("9140", "2024-05-12T17:00:00Z")); // After session end
    }

    @Test
    @DisplayName("seek updates current time")
    void testSeekUpdatesCurrentTime() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        PlaybackState state = replayService.seek("9140", "2024-05-12T15:00:00Z");

        assertEquals("2024-05-12T15:00:00Z", state.getCurrentTime());
    }

    @Test
    @DisplayName("setSpeed throws exception when no active session")
    void testSetSpeedNoActiveSession() {
        assertThrows(IllegalArgumentException.class, () ->
                replayService.setSpeed("9140", PlaybackSpeed.DOUBLE));
    }

    @Test
    @DisplayName("setSpeed updates playback speed")
    void testSetSpeedUpdatesSpeed() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        PlaybackState state = replayService.setSpeed("9140", PlaybackSpeed.FAST);

        assertEquals(PlaybackSpeed.FAST, state.getSpeed());
    }

    @Test
    @DisplayName("getState returns null when no active session")
    void testGetStateNoActiveSession() {
        assertNull(replayService.getState("9140"));
    }

    @Test
    @DisplayName("getState returns current state")
    void testGetStateReturnsState() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        PlaybackState state = replayService.getState("9140");

        assertNotNull(state);
        assertEquals("9140", state.getSessionKey());
        assertEquals(PlaybackStatus.PLAYING, state.getStatus());
    }

    @Test
    @DisplayName("isSessionActive returns false when no session")
    void testIsSessionActiveNoSession() {
        assertFalse(replayService.isSessionActive("9140"));
    }

    @Test
    @DisplayName("isSessionActive returns true for active session")
    void testIsSessionActiveTrue() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        assertTrue(replayService.isSessionActive("9140"));
    }

    @Test
    @DisplayName("getNextBatch returns null when session not playing")
    void testGetNextBatchNotPlaying() {
        assertNull(replayService.getNextBatch("9140"));
    }

    @Test
    @DisplayName("onClientDisconnect preserves state")
    void testOnClientDisconnect() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.getSession("9140")).thenReturn(Optional.of(testSession));
        when(redisStreamService.readLocationsByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(redisStreamService.readCarDataByTimeRange(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        replayService.play("9140", "2024-05-12T14:00:00Z");

        // Disconnect
        replayService.onClientDisconnect("9140");

        // Session should no longer be active
        assertFalse(replayService.isSessionActive("9140"));

        // But state should be preserved
        PlaybackState state = replayService.getState("9140");
        assertNotNull(state);
        assertEquals(PlaybackStatus.PAUSED, state.getStatus());
    }
}
