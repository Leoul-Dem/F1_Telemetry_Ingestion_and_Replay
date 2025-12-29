package com.f1replay.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackSpeedTest {

    @Test
    @DisplayName("PlaybackSpeed enum values have correct multipliers")
    void testPlaybackSpeedMultipliers() {
        assertEquals(1.0, PlaybackSpeed.NORMAL.getMultiplier());
        assertEquals(2.0, PlaybackSpeed.DOUBLE.getMultiplier());
        assertEquals(5.0, PlaybackSpeed.FAST.getMultiplier());
        assertEquals(10.0, PlaybackSpeed.SUPER_FAST.getMultiplier());
    }

    @Test
    @DisplayName("fromMultiplier returns correct enum for valid multipliers")
    void testFromMultiplierValid() {
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.fromMultiplier(1.0));
        assertEquals(PlaybackSpeed.DOUBLE, PlaybackSpeed.fromMultiplier(2.0));
        assertEquals(PlaybackSpeed.FAST, PlaybackSpeed.fromMultiplier(5.0));
        assertEquals(PlaybackSpeed.SUPER_FAST, PlaybackSpeed.fromMultiplier(10.0));
    }

    @Test
    @DisplayName("fromMultiplier throws exception for invalid multipliers")
    void testFromMultiplierInvalid() {
        assertThrows(IllegalArgumentException.class, () -> PlaybackSpeed.fromMultiplier(3.0));
        assertThrows(IllegalArgumentException.class, () -> PlaybackSpeed.fromMultiplier(0.5));
        assertThrows(IllegalArgumentException.class, () -> PlaybackSpeed.fromMultiplier(-1.0));
    }
}
