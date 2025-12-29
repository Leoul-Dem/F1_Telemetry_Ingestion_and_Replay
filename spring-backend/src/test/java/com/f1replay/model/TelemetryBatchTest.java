package com.f1replay.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryBatchTest {

    @Test
    @DisplayName("TelemetryBatch builder creates object with correct values")
    void testTelemetryBatchBuilder() {
        List<LocationPoint> locations = Arrays.asList(
                LocationPoint.builder().driverNumber(1).x(100.0).y(200.0).build(),
                LocationPoint.builder().driverNumber(44).x(150.0).y(250.0).build()
        );

        List<CarData> carData = Arrays.asList(
                CarData.builder().driverNumber(1).speed(300).build(),
                CarData.builder().driverNumber(44).speed(310).build()
        );

        TelemetryBatch batch = TelemetryBatch.builder()
                .batchTimestamp("2024-05-12T14:00:00Z")
                .locations(locations)
                .carData(carData)
                .build();

        assertEquals("2024-05-12T14:00:00Z", batch.getBatchTimestamp());
        assertEquals(2, batch.getLocations().size());
        assertEquals(2, batch.getCarData().size());
        assertEquals(1, batch.getLocations().get(0).getDriverNumber());
        assertEquals(44, batch.getLocations().get(1).getDriverNumber());
    }

    @Test
    @DisplayName("TelemetryBatch with empty lists")
    void testTelemetryBatchEmptyLists() {
        TelemetryBatch batch = TelemetryBatch.builder()
                .batchTimestamp("2024-05-12T14:00:00Z")
                .locations(Collections.emptyList())
                .carData(Collections.emptyList())
                .build();

        assertNotNull(batch.getLocations());
        assertNotNull(batch.getCarData());
        assertTrue(batch.getLocations().isEmpty());
        assertTrue(batch.getCarData().isEmpty());
    }

    @Test
    @DisplayName("TelemetryBatch default constructor")
    void testTelemetryBatchDefaultConstructor() {
        TelemetryBatch batch = new TelemetryBatch();
        assertNull(batch.getBatchTimestamp());
        assertNull(batch.getLocations());
        assertNull(batch.getCarData());
    }
}
