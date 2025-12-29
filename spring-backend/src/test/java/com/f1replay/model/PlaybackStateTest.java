package com.f1replay.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackStateTest {

    @Test
    @DisplayName("PlaybackState builder creates object with correct values")
    void testPlaybackStateBuilder() {
        PlaybackState state = PlaybackState.builder()
                .sessionKey("9140")
                .status(PlaybackStatus.PLAYING)
                .currentTime("2024-05-12T14:30:00Z")
                .startTime("2024-05-12T14:00:00Z")
                .endTime("2024-05-12T16:00:00Z")
                .speed(PlaybackSpeed.DOUBLE)
                .durationMs(7200000L)
                .elapsedMs(1800000L)
                .build();

        assertEquals("9140", state.getSessionKey());
        assertEquals(PlaybackStatus.PLAYING, state.getStatus());
        assertEquals("2024-05-12T14:30:00Z", state.getCurrentTime());
        assertEquals("2024-05-12T14:00:00Z", state.getStartTime());
        assertEquals("2024-05-12T16:00:00Z", state.getEndTime());
        assertEquals(PlaybackSpeed.DOUBLE, state.getSpeed());
        assertEquals(7200000L, state.getDurationMs());
        assertEquals(1800000L, state.getElapsedMs());
    }

    @Test
    @DisplayName("PlaybackState status transitions")
    void testPlaybackStateStatusTransitions() {
        PlaybackState state = PlaybackState.builder()
                .sessionKey("9140")
                .status(PlaybackStatus.STOPPED)
                .build();

        assertEquals(PlaybackStatus.STOPPED, state.getStatus());

        state.setStatus(PlaybackStatus.PLAYING);
        assertEquals(PlaybackStatus.PLAYING, state.getStatus());

        state.setStatus(PlaybackStatus.PAUSED);
        assertEquals(PlaybackStatus.PAUSED, state.getStatus());

        state.setStatus(PlaybackStatus.STOPPED);
        assertEquals(PlaybackStatus.STOPPED, state.getStatus());
    }

    @Test
    @DisplayName("PlaybackState elapsed time calculation")
    void testPlaybackStateElapsedTime() {
        // 2 hour session, 30 minutes elapsed
        PlaybackState state = PlaybackState.builder()
                .durationMs(7200000L) // 2 hours in ms
                .elapsedMs(1800000L)  // 30 minutes in ms
                .build();

        // Verify values
        assertEquals(7200000L, state.getDurationMs());
        assertEquals(1800000L, state.getElapsedMs());

        // Calculate percentage
        double percentComplete = (double) state.getElapsedMs() / state.getDurationMs() * 100;
        assertEquals(25.0, percentComplete, 0.001);
    }
}
