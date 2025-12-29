package com.f1replay.service;

import com.f1replay.config.ReplayProperties;
import com.f1replay.model.SessionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private ReplayProperties replayProperties;

    @Mock
    private RedisStreamService redisStreamService;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        // Setup mock sessions in config
        ReplayProperties.SessionConfig session1 = new ReplayProperties.SessionConfig();
        session1.setKey("9140");
        session1.setName("Monaco Grand Prix 2024 - Race");
        session1.setDateStart("2024-05-12T14:00:00Z");
        session1.setDateEnd("2024-05-12T16:00:00Z");

        ReplayProperties.SessionConfig session2 = new ReplayProperties.SessionConfig();
        session2.setKey("9141");
        session2.setName("Monaco Grand Prix 2024 - Qualifying");
        session2.setDateStart("2024-05-11T15:00:00Z");
        session2.setDateEnd("2024-05-11T16:00:00Z");

        when(replayProperties.getSessions()).thenReturn(Arrays.asList(session1, session2));

        // Mock Redis stream length calls
        when(redisStreamService.getLocationStreamKey("9140")).thenReturn("telemetry:location:9140");
        when(redisStreamService.getCarDataStreamKey("9140")).thenReturn("telemetry:cardata:9140");
        when(redisStreamService.getLocationStreamKey("9141")).thenReturn("telemetry:location:9141");
        when(redisStreamService.getCarDataStreamKey("9141")).thenReturn("telemetry:cardata:9141");
        when(redisStreamService.getStreamLength(anyString())).thenReturn(1000L);

        sessionService = new SessionService(replayProperties, redisStreamService);
        sessionService.init();
    }

    @Test
    @DisplayName("getAllSessions returns all configured sessions")
    void testGetAllSessions() {
        List<SessionInfo> sessions = sessionService.getAllSessions();

        assertEquals(2, sessions.size());
    }

    @Test
    @DisplayName("getSession returns session for valid key")
    void testGetSessionValid() {
        Optional<SessionInfo> session = sessionService.getSession("9140");

        assertTrue(session.isPresent());
        assertEquals("9140", session.get().getSessionKey());
        assertEquals("Monaco Grand Prix 2024 - Race", session.get().getName());
        assertEquals("2024-05-12T14:00:00Z", session.get().getDateStart());
        assertEquals("2024-05-12T16:00:00Z", session.get().getDateEnd());
    }

    @Test
    @DisplayName("getSession returns empty for invalid key")
    void testGetSessionInvalid() {
        Optional<SessionInfo> session = sessionService.getSession("invalid");

        assertTrue(session.isEmpty());
    }

    @Test
    @DisplayName("sessionExists returns true for valid session")
    void testSessionExistsTrue() {
        assertTrue(sessionService.sessionExists("9140"));
        assertTrue(sessionService.sessionExists("9141"));
    }

    @Test
    @DisplayName("sessionExists returns false for invalid session")
    void testSessionExistsFalse() {
        assertFalse(sessionService.sessionExists("invalid"));
        assertFalse(sessionService.sessionExists(""));
    }

    @Test
    @DisplayName("sessionHasData checks Redis stream")
    void testSessionHasData() {
        when(redisStreamService.streamExists("telemetry:location:9140")).thenReturn(true);

        assertTrue(sessionService.sessionHasData("9140"));
        verify(redisStreamService).streamExists("telemetry:location:9140");
    }

    @Test
    @DisplayName("sessionHasData returns false when no data")
    void testSessionHasDataFalse() {
        when(redisStreamService.streamExists("telemetry:location:9141")).thenReturn(false);

        assertFalse(sessionService.sessionHasData("9141"));
    }

    @Test
    @DisplayName("refreshSession updates session metadata")
    void testRefreshSession() {
        when(redisStreamService.getStreamLength("telemetry:location:9140")).thenReturn(5000L);
        when(redisStreamService.getStreamLength("telemetry:cardata:9140")).thenReturn(10000L);

        SessionInfo refreshed = sessionService.refreshSession("9140");

        assertNotNull(refreshed);
        assertEquals("9140", refreshed.getSessionKey());
        assertEquals(5000, refreshed.getLocationCount());
        assertEquals(10000, refreshed.getCarDataCount());
    }

    @Test
    @DisplayName("refreshSession returns null for invalid session")
    void testRefreshSessionInvalid() {
        SessionInfo refreshed = sessionService.refreshSession("invalid");

        assertNull(refreshed);
    }

    @Test
    @DisplayName("Session duration is calculated correctly")
    void testSessionDuration() {
        Optional<SessionInfo> session = sessionService.getSession("9140");

        assertTrue(session.isPresent());
        // 2 hours = 7,200,000 ms
        assertEquals(7200000L, session.get().getDurationMs());
    }
}
