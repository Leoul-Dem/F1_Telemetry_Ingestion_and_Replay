package com.f1replay.controller;

import com.f1replay.model.SessionInfo;
import com.f1replay.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock
    private SessionService sessionService;

    private SessionController sessionController;

    @BeforeEach
    void setUp() {
        sessionController = new SessionController(sessionService);
    }

    private SessionInfo createTestSession(String key, String name) {
        return SessionInfo.builder()
                .sessionKey(key)
                .name(name)
                .dateStart("2024-05-12T14:00:00Z")
                .dateEnd("2024-05-12T16:00:00Z")
                .durationMs(7200000L)
                .locationCount(50000)
                .carDataCount(100000)
                .build();
    }

    @Test
    @DisplayName("getAllSessions() returns all sessions")
    void testGetAllSessions() {
        List<SessionInfo> sessions = Arrays.asList(
                createTestSession("9140", "Monaco GP - Race"),
                createTestSession("9141", "Monaco GP - Qualifying")
        );
        when(sessionService.getAllSessions()).thenReturn(sessions);

        ResponseEntity<List<SessionInfo>> response = sessionController.getAllSessions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("9140", response.getBody().get(0).getSessionKey());
    }

    @Test
    @DisplayName("getAllSessions() returns empty list when no sessions")
    void testGetAllSessionsEmpty() {
        when(sessionService.getAllSessions()).thenReturn(List.of());

        ResponseEntity<List<SessionInfo>> response = sessionController.getAllSessions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    @DisplayName("getSession() returns session details")
    void testGetSession() {
        SessionInfo session = createTestSession("9140", "Monaco GP - Race");
        when(sessionService.getSession("9140")).thenReturn(Optional.of(session));

        ResponseEntity<SessionInfo> response = sessionController.getSession("9140");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("9140", response.getBody().getSessionKey());
        assertEquals("Monaco GP - Race", response.getBody().getName());
    }

    @Test
    @DisplayName("getSession() returns 404 for invalid session")
    void testGetSessionNotFound() {
        when(sessionService.getSession("invalid")).thenReturn(Optional.empty());

        ResponseEntity<SessionInfo> response = sessionController.getSession("invalid");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("refreshSession() refreshes session metadata")
    void testRefreshSession() {
        SessionInfo refreshed = createTestSession("9140", "Monaco GP - Race");
        when(sessionService.refreshSession("9140")).thenReturn(refreshed);

        ResponseEntity<SessionInfo> response = sessionController.refreshSession("9140");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("9140", response.getBody().getSessionKey());
    }

    @Test
    @DisplayName("refreshSession() returns 404 for invalid session")
    void testRefreshSessionNotFound() {
        when(sessionService.refreshSession("invalid")).thenReturn(null);

        ResponseEntity<SessionInfo> response = sessionController.refreshSession("invalid");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("getSessionStatus() returns session status with data")
    void testGetSessionStatus() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.sessionHasData("9140")).thenReturn(true);

        ResponseEntity<SessionController.SessionStatusResponse> response = 
                sessionController.getSessionStatus("9140");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("9140", response.getBody().sessionKey());
        assertTrue(response.getBody().hasData());
    }

    @Test
    @DisplayName("getSessionStatus() returns hasData false when no data")
    void testGetSessionStatusNoData() {
        when(sessionService.sessionExists("9140")).thenReturn(true);
        when(sessionService.sessionHasData("9140")).thenReturn(false);

        ResponseEntity<SessionController.SessionStatusResponse> response = 
                sessionController.getSessionStatus("9140");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().hasData());
    }

    @Test
    @DisplayName("getSessionStatus() returns 404 for invalid session")
    void testGetSessionStatusNotFound() {
        when(sessionService.sessionExists("invalid")).thenReturn(false);

        ResponseEntity<SessionController.SessionStatusResponse> response = 
                sessionController.getSessionStatus("invalid");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
