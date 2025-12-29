package com.f1replay.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class LocationPointTest {

    @Test
    @DisplayName("LocationPoint builder creates object with correct values")
    void testLocationPointBuilder() {
        LocationPoint point = LocationPoint.builder()
                .sessionKey(9140)
                .driverNumber(1)
                .timestamp("2024-05-12T14:00:00Z")
                .x(1234.567)
                .y(8901.234)
                .build();

        assertEquals(9140, point.getSessionKey());
        assertEquals(1, point.getDriverNumber());
        assertEquals("2024-05-12T14:00:00Z", point.getTimestamp());
        assertEquals(1234.567, point.getX(), 0.001);
        assertEquals(8901.234, point.getY(), 0.001);
    }

    @Test
    @DisplayName("LocationPoint default constructor creates empty object")
    void testLocationPointDefaultConstructor() {
        LocationPoint point = new LocationPoint();
        assertEquals(0, point.getSessionKey());
        assertEquals(0, point.getDriverNumber());
        assertNull(point.getTimestamp());
        assertEquals(0.0, point.getX());
        assertEquals(0.0, point.getY());
    }

    @Test
    @DisplayName("LocationPoint setters work correctly")
    void testLocationPointSetters() {
        LocationPoint point = new LocationPoint();
        point.setSessionKey(9140);
        point.setDriverNumber(44);
        point.setTimestamp("2024-05-12T14:00:00Z");
        point.setX(100.0);
        point.setY(200.0);

        assertEquals(9140, point.getSessionKey());
        assertEquals(44, point.getDriverNumber());
        assertEquals("2024-05-12T14:00:00Z", point.getTimestamp());
        assertEquals(100.0, point.getX());
        assertEquals(200.0, point.getY());
    }

    @Test
    @DisplayName("LocationPoint equals and hashCode work correctly")
    void testLocationPointEqualsAndHashCode() {
        LocationPoint point1 = LocationPoint.builder()
                .sessionKey(9140)
                .driverNumber(1)
                .timestamp("2024-05-12T14:00:00Z")
                .x(100.0)
                .y(200.0)
                .build();

        LocationPoint point2 = LocationPoint.builder()
                .sessionKey(9140)
                .driverNumber(1)
                .timestamp("2024-05-12T14:00:00Z")
                .x(100.0)
                .y(200.0)
                .build();

        assertEquals(point1, point2);
        assertEquals(point1.hashCode(), point2.hashCode());
    }
}
