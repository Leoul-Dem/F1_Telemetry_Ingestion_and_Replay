package com.f1replay.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CarDataTest {

    @Test
    @DisplayName("CarData builder creates object with correct values")
    void testCarDataBuilder() {
        CarData carData = CarData.builder()
                .sessionKey(9140)
                .driverNumber(44)
                .timestamp("2024-05-12T14:00:00Z")
                .speed(325)
                .rpm(12500)
                .gear(8)
                .throttle(100)
                .brake(0)
                .build();

        assertEquals(9140, carData.getSessionKey());
        assertEquals(44, carData.getDriverNumber());
        assertEquals("2024-05-12T14:00:00Z", carData.getTimestamp());
        assertEquals(325, carData.getSpeed());
        assertEquals(12500, carData.getRpm());
        assertEquals(8, carData.getGear());
        assertEquals(100, carData.getThrottle());
        assertEquals(0, carData.getBrake());
    }

    @Test
    @DisplayName("CarData default constructor creates object with default values")
    void testCarDataDefaultConstructor() {
        CarData carData = new CarData();
        assertEquals(0, carData.getSessionKey());
        assertEquals(0, carData.getDriverNumber());
        assertNull(carData.getTimestamp());
        assertEquals(0, carData.getSpeed());
        assertEquals(0, carData.getRpm());
        assertEquals(0, carData.getGear());
        assertEquals(0, carData.getThrottle());
        assertEquals(0, carData.getBrake());
    }

    @Test
    @DisplayName("CarData setters work correctly")
    void testCarDataSetters() {
        CarData carData = new CarData();
        carData.setSessionKey(9140);
        carData.setDriverNumber(1);
        carData.setTimestamp("2024-05-12T14:00:00Z");
        carData.setSpeed(300);
        carData.setRpm(11000);
        carData.setGear(7);
        carData.setThrottle(80);
        carData.setBrake(20);

        assertEquals(9140, carData.getSessionKey());
        assertEquals(1, carData.getDriverNumber());
        assertEquals("2024-05-12T14:00:00Z", carData.getTimestamp());
        assertEquals(300, carData.getSpeed());
        assertEquals(11000, carData.getRpm());
        assertEquals(7, carData.getGear());
        assertEquals(80, carData.getThrottle());
        assertEquals(20, carData.getBrake());
    }

    @Test
    @DisplayName("CarData equals and hashCode work correctly")
    void testCarDataEqualsAndHashCode() {
        CarData carData1 = CarData.builder()
                .sessionKey(9140)
                .driverNumber(44)
                .timestamp("2024-05-12T14:00:00Z")
                .speed(325)
                .rpm(12500)
                .gear(8)
                .throttle(100)
                .brake(0)
                .build();

        CarData carData2 = CarData.builder()
                .sessionKey(9140)
                .driverNumber(44)
                .timestamp("2024-05-12T14:00:00Z")
                .speed(325)
                .rpm(12500)
                .gear(8)
                .throttle(100)
                .brake(0)
                .build();

        assertEquals(carData1, carData2);
        assertEquals(carData1.hashCode(), carData2.hashCode());
    }
}
