package com.f1replay.controller;

import com.f1replay.model.*;
import com.f1replay.service.ReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaybackControllerTest {

    @Mock
    private ReplayService replayService;

    private PlaybackController playbackController;

    @BeforeEach
    void setUp() {
        playbackController = new PlaybackController(replayService);
    }

    private PlaybackState createTestState(PlaybackStatus status) {
        return PlaybackState.builder()
                .sessionKey("9140")
                .status(status)
                .currentTime("2024-05-12T14:30:00Z")
                .startTime("2024-05-12T14:00:00Z")
                .endTime("2024-05-12T16:00:00Z")
                .speed(PlaybackSpeed.NORMAL)
                .durationMs(7200000L)
                .elapsedMs(1800000L)
                .build();
    }

    @Test
    @DisplayName("play() starts playback and returns PLAYING state")
    void testPlayStartsPlayback() {
        PlaybackState state = createTestState(PlaybackStatus.PLAYING);
        when(replayService.play(eq("9140"), any())).thenReturn(state);

        PlaybackController.PlayRequest request = new PlaybackController.PlayRequest("2024-05-12T14:00:00Z");
        ResponseEntity<PlaybackState> response = playbackController.play("9140", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("9140", response.getBody().getSessionKey());
        assertEquals(PlaybackStatus.PLAYING, response.getBody().getStatus());
    }

    @Test
    @DisplayName("play() returns 400 for invalid session")
    void testPlayInvalidSession() {
        when(replayService.play(eq("invalid"), any()))
                .thenThrow(new IllegalArgumentException("Session not found"));

        ResponseEntity<PlaybackState> response = playbackController.play("invalid", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("pause() pauses playback")
    void testPausePausesPlayback() {
        PlaybackState state = createTestState(PlaybackStatus.PAUSED);
        when(replayService.pause("9140")).thenReturn(state);

        ResponseEntity<PlaybackState> response = playbackController.pause("9140");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(PlaybackStatus.PAUSED, response.getBody().getStatus());
    }

    @Test
    @DisplayName("pause() returns 400 for no active session")
    void testPauseNoActiveSession() {
        when(replayService.pause("9140"))
                .thenThrow(new IllegalArgumentException("No active session"));

        ResponseEntity<PlaybackState> response = playbackController.pause("9140");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("stop() stops playback")
    void testStopStopsPlayback() {
        PlaybackState state = createTestState(PlaybackStatus.STOPPED);
        when(replayService.stop("9140")).thenReturn(state);

        ResponseEntity<PlaybackState> response = playbackController.stop("9140");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(PlaybackStatus.STOPPED, response.getBody().getStatus());
    }

    @Test
    @DisplayName("seek() seeks to target time")
    void testSeekToTargetTime() {
        PlaybackState state = PlaybackState.builder()
                .sessionKey("9140")
                .status(PlaybackStatus.PLAYING)
                .currentTime("2024-05-12T15:00:00Z")
                .build();
        when(replayService.seek(eq("9140"), eq("2024-05-12T15:00:00Z"))).thenReturn(state);

        PlaybackController.SeekRequest request = new PlaybackController.SeekRequest("2024-05-12T15:00:00Z");
        ResponseEntity<PlaybackState> response = playbackController.seek("9140", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("2024-05-12T15:00:00Z", response.getBody().getCurrentTime());
    }

    @Test
    @DisplayName("setSpeed() changes speed")
    void testChangeSpeed() {
        PlaybackState state = PlaybackState.builder()
                .sessionKey("9140")
                .status(PlaybackStatus.PLAYING)
                .speed(PlaybackSpeed.DOUBLE)
                .build();
        when(replayService.setSpeed(eq("9140"), eq(PlaybackSpeed.DOUBLE))).thenReturn(state);

        PlaybackController.SpeedRequest request = new PlaybackController.SpeedRequest(2.0);
        ResponseEntity<PlaybackState> response = playbackController.setSpeed("9140", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(PlaybackSpeed.DOUBLE, response.getBody().getSpeed());
    }

    @Test
    @DisplayName("setSpeed() returns 400 for invalid speed")
    void testChangeSpeedInvalid() {
        PlaybackController.SpeedRequest request = new PlaybackController.SpeedRequest(3.5);
        ResponseEntity<PlaybackState> response = playbackController.setSpeed("9140", request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("getState() returns current state")
    void testGetState() {
        PlaybackState state = createTestState(PlaybackStatus.PLAYING);
        when(replayService.getState("9140")).thenReturn(state);

        ResponseEntity<PlaybackState> response = playbackController.getState("9140");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("9140", response.getBody().getSessionKey());
        assertEquals(PlaybackStatus.PLAYING, response.getBody().getStatus());
    }

    @Test
    @DisplayName("getState() returns 404 for no session")
    void testGetStateNotFound() {
        when(replayService.getState("9140")).thenReturn(null);

        ResponseEntity<PlaybackState> response = playbackController.getState("9140");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
