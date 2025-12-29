package com.f1replay.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class SessionInfoTest {

    @Test
    @DisplayName("SessionInfo builder creates object with correct values")
    void testSessionInfoBuilder() {
        SessionInfo info = SessionInfo.builder()
                .sessionKey("9140")
                .name("Monaco Grand Prix 2024 - Race")
                .dateStart("2024-05-12T14:00:00Z")
                .dateEnd("2024-05-12T16:00:00Z")
                .durationMs(7200000L)
                .locationCount(50000)
                .carDataCount(100000)
                .build();

        assertEquals("9140", info.getSessionKey());
        assertEquals("Monaco Grand Prix 2024 - Race", info.getName());
        assertEquals("2024-05-12T14:00:00Z", info.getDateStart());
        assertEquals("2024-05-12T16:00:00Z", info.getDateEnd());
        assertEquals(7200000L, info.getDurationMs());
        assertEquals(50000, info.getLocationCount());
        assertEquals(100000, info.getCarDataCount());
    }

    @Test
    @DisplayName("SessionInfo with null counts (before data loaded)")
    void testSessionInfoNullCounts() {
        SessionInfo info = SessionInfo.builder()
                .sessionKey("9140")
                .name("Monaco Grand Prix 2024 - Race")
                .dateStart("2024-05-12T14:00:00Z")
                .dateEnd("2024-05-12T16:00:00Z")
                .build();

        assertEquals("9140", info.getSessionKey());
        assertNull(info.getDurationMs());
        assertNull(info.getLocationCount());
        assertNull(info.getCarDataCount());
    }

    @Test
    @DisplayName("SessionInfo equals and hashCode work correctly")
    void testSessionInfoEqualsAndHashCode() {
        SessionInfo info1 = SessionInfo.builder()
                .sessionKey("9140")
                .name("Monaco Grand Prix 2024 - Race")
                .dateStart("2024-05-12T14:00:00Z")
                .dateEnd("2024-05-12T16:00:00Z")
                .build();

        SessionInfo info2 = SessionInfo.builder()
                .sessionKey("9140")
                .name("Monaco Grand Prix 2024 - Race")
                .dateStart("2024-05-12T14:00:00Z")
                .dateEnd("2024-05-12T16:00:00Z")
                .build();

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }
}
