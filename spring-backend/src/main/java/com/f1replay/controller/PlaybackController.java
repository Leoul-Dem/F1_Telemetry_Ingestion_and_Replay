package com.f1replay.controller;

import com.f1replay.model.PlaybackSpeed;
import com.f1replay.model.PlaybackState;
import com.f1replay.service.ReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
public class PlaybackController {

    private final ReplayService replayService;

    /**
     * Start or resume playback
     * POST /api/replay/{sessionKey}/play
     */
    @PostMapping("/{sessionKey}/play")
    public ResponseEntity<PlaybackState> play(
            @PathVariable String sessionKey,
            @RequestBody(required = false) PlayRequest request) {

        log.info("Play request for session: {}", sessionKey);
        try {
            String startTime = request != null ? request.startTime() : null;
            PlaybackState state = replayService.play(sessionKey, startTime);
            return ResponseEntity.ok(state);
        } catch (IllegalArgumentException e) {
            log.warn("Play request failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Pause playback
     * POST /api/replay/{sessionKey}/pause
     */
    @PostMapping("/{sessionKey}/pause")
    public ResponseEntity<PlaybackState> pause(@PathVariable String sessionKey) {
        log.info("Pause request for session: {}", sessionKey);
        try {
            PlaybackState state = replayService.pause(sessionKey);
            return ResponseEntity.ok(state);
        } catch (IllegalArgumentException e) {
            log.warn("Pause request failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Stop playback
     * POST /api/replay/{sessionKey}/stop
     */
    @PostMapping("/{sessionKey}/stop")
    public ResponseEntity<PlaybackState> stop(@PathVariable String sessionKey) {
        log.info("Stop request for session: {}", sessionKey);
        try {
            PlaybackState state = replayService.stop(sessionKey);
            return ResponseEntity.ok(state);
        } catch (IllegalArgumentException e) {
            log.warn("Stop request failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Seek to a specific timestamp
     * POST /api/replay/{sessionKey}/seek
     */
    @PostMapping("/{sessionKey}/seek")
    public ResponseEntity<PlaybackState> seek(
            @PathVariable String sessionKey,
            @RequestBody SeekRequest request) {

        log.info("Seek request for session {} to {}", sessionKey, request.targetTime());
        try {
            PlaybackState state = replayService.seek(sessionKey, request.targetTime());
            return ResponseEntity.ok(state);
        } catch (IllegalArgumentException e) {
            log.warn("Seek request failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Set playback speed
     * POST /api/replay/{sessionKey}/speed
     */
    @PostMapping("/{sessionKey}/speed")
    public ResponseEntity<PlaybackState> setSpeed(
            @PathVariable String sessionKey,
            @RequestBody SpeedRequest request) {

        log.info("Speed change request for session {} to {}x", sessionKey, request.speed());
        try {
            PlaybackSpeed speed = PlaybackSpeed.fromMultiplier(request.speed());
            PlaybackState state = replayService.setSpeed(sessionKey, speed);
            return ResponseEntity.ok(state);
        } catch (IllegalArgumentException e) {
            log.warn("Speed change failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get current playback state
     * GET /api/replay/{sessionKey}/state
     */
    @GetMapping("/{sessionKey}/state")
    public ResponseEntity<PlaybackState> getState(@PathVariable String sessionKey) {
        PlaybackState state = replayService.getState(sessionKey);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    // Request DTOs
    public record PlayRequest(String startTime) {}
    public record SeekRequest(String targetTime) {}
    public record SpeedRequest(double speed) {}
}
