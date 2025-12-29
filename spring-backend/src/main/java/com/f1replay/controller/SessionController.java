package com.f1replay.controller;

import com.f1replay.model.SessionInfo;
import com.f1replay.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * Get all available sessions
     * GET /api/sessions
     */
    @GetMapping
    public ResponseEntity<List<SessionInfo>> getAllSessions() {
        log.debug("Fetching all sessions");
        List<SessionInfo> sessions = sessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get a specific session by key
     * GET /api/sessions/{sessionKey}
     */
    @GetMapping("/{sessionKey}")
    public ResponseEntity<SessionInfo> getSession(@PathVariable String sessionKey) {
        log.debug("Fetching session: {}", sessionKey);
        return sessionService.getSession(sessionKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Refresh session metadata from Redis
     * POST /api/sessions/{sessionKey}/refresh
     */
    @PostMapping("/{sessionKey}/refresh")
    public ResponseEntity<SessionInfo> refreshSession(@PathVariable String sessionKey) {
        log.debug("Refreshing session: {}", sessionKey);
        SessionInfo refreshed = sessionService.refreshSession(sessionKey);
        if (refreshed == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(refreshed);
    }

    /**
     * Check if session has data in Redis
     * GET /api/sessions/{sessionKey}/status
     */
    @GetMapping("/{sessionKey}/status")
    public ResponseEntity<SessionStatusResponse> getSessionStatus(@PathVariable String sessionKey) {
        if (!sessionService.sessionExists(sessionKey)) {
            return ResponseEntity.notFound().build();
        }

        boolean hasData = sessionService.sessionHasData(sessionKey);
        return ResponseEntity.ok(new SessionStatusResponse(sessionKey, hasData));
    }

    // Simple response record for session status
    public record SessionStatusResponse(String sessionKey, boolean hasData) {}
}
