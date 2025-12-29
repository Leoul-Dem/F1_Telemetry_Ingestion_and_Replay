package com.f1replay.service;

import com.f1replay.model.CarData;
import com.f1replay.model.LocationPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisStreamServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private RedisStreamService redisStreamService;

    @BeforeEach
    void setUp() {
        redisStreamService = new RedisStreamService(redisTemplate);
    }

    @Test
    @DisplayName("getLocationStreamKey returns correct format")
    void testGetLocationStreamKey() {
        String key = redisStreamService.getLocationStreamKey("9140");
        assertEquals("telemetry:location:9140", key);
    }

    @Test
    @DisplayName("getCarDataStreamKey returns correct format")
    void testGetCarDataStreamKey() {
        String key = redisStreamService.getCarDataStreamKey("9140");
        assertEquals("telemetry:cardata:9140", key);
    }

    @Test
    @DisplayName("getStreamLength returns stream size")
    void testGetStreamLength() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.size("telemetry:location:9140")).thenReturn(1000L);

        Long length = redisStreamService.getStreamLength("telemetry:location:9140");
        assertEquals(1000L, length);
    }

    @Test
    @DisplayName("getStreamLength returns 0 on error")
    void testGetStreamLengthOnError() {
        when(redisTemplate.opsForStream()).thenThrow(new RuntimeException("Connection error"));

        Long length = redisStreamService.getStreamLength("telemetry:location:9140");
        assertEquals(0L, length);
    }

    @Test
    @DisplayName("streamExists returns true for existing stream")
    void testStreamExistsTrue() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.size("telemetry:location:9140")).thenReturn(100L);

        assertTrue(redisStreamService.streamExists("telemetry:location:9140"));
    }

    @Test
    @DisplayName("streamExists returns false for empty stream")
    void testStreamExistsFalse() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.size("telemetry:location:9140")).thenReturn(0L);

        assertFalse(redisStreamService.streamExists("telemetry:location:9140"));
    }

    @Test
    @DisplayName("streamExists returns false on error")
    void testStreamExistsOnError() {
        when(redisTemplate.opsForStream()).thenThrow(new RuntimeException("Connection error"));

        assertFalse(redisStreamService.streamExists("telemetry:location:9140"));
    }

    @Test
    @DisplayName("readLocationsByTimeRange returns filtered locations")
    void testReadLocationsByTimeRange() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        // Create mock records
        Map<Object, Object> values1 = new HashMap<>();
        values1.put("driver_number", "1");
        values1.put("x", "100.5");
        values1.put("y", "200.5");
        values1.put("timestamp", "2024-05-12T14:00:30Z");

        Map<Object, Object> values2 = new HashMap<>();
        values2.put("driver_number", "44");
        values2.put("x", "150.5");
        values2.put("y", "250.5");
        values2.put("timestamp", "2024-05-12T14:01:00Z");

        // Record outside time range
        Map<Object, Object> values3 = new HashMap<>();
        values3.put("driver_number", "16");
        values3.put("x", "180.0");
        values3.put("y", "280.0");
        values3.put("timestamp", "2024-05-12T14:05:00Z"); // Outside range

        List<MapRecord<String, Object, Object>> mockRecords = Arrays.asList(
                MapRecord.create("telemetry:location:9140", values1).withId(RecordId.of("1715522430000-0")),
                MapRecord.create("telemetry:location:9140", values2).withId(RecordId.of("1715522460000-0")),
                MapRecord.create("telemetry:location:9140", values3).withId(RecordId.of("1715522700000-0"))
        );

        when(streamOperations.range(eq("telemetry:location:9140"), any(Range.class)))
                .thenReturn(mockRecords);

        List<LocationPoint> locations = redisStreamService.readLocationsByTimeRange(
                "9140",
                "2024-05-12T14:00:00Z",
                "2024-05-12T14:02:00Z"
        );

        assertEquals(2, locations.size());
        assertEquals(1, locations.get(0).getDriverNumber());
        assertEquals(44, locations.get(1).getDriverNumber());
    }

    @Test
    @DisplayName("readCarDataByTimeRange returns filtered car data")
    void testReadCarDataByTimeRange() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        Map<Object, Object> values1 = new HashMap<>();
        values1.put("driver_number", "1");
        values1.put("speed", "320");
        values1.put("rpm", "12000");
        values1.put("gear", "8");
        values1.put("throttle", "100");
        values1.put("brake", "0");
        values1.put("timestamp", "2024-05-12T14:00:30Z");

        List<MapRecord<String, Object, Object>> mockRecords = Collections.singletonList(
                MapRecord.create("telemetry:cardata:9140", values1).withId(RecordId.of("1715522430000-0"))
        );

        when(streamOperations.range(eq("telemetry:cardata:9140"), any(Range.class)))
                .thenReturn(mockRecords);

        List<CarData> carData = redisStreamService.readCarDataByTimeRange(
                "9140",
                "2024-05-12T14:00:00Z",
                "2024-05-12T14:02:00Z"
        );

        assertEquals(1, carData.size());
        assertEquals(1, carData.get(0).getDriverNumber());
        assertEquals(320, carData.get(0).getSpeed());
        assertEquals(12000, carData.get(0).getRpm());
        assertEquals(8, carData.get(0).getGear());
    }

    @Test
    @DisplayName("readLocationsByTimeRange handles empty stream")
    void testReadLocationsByTimeRangeEmpty() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.range(anyString(), any(Range.class)))
                .thenReturn(Collections.emptyList());

        List<LocationPoint> locations = redisStreamService.readLocationsByTimeRange(
                "9140",
                "2024-05-12T14:00:00Z",
                "2024-05-12T14:02:00Z"
        );

        assertTrue(locations.isEmpty());
    }

    @Test
    @DisplayName("readLocationsByTimeRange handles Redis error")
    void testReadLocationsByTimeRangeError() {
        when(redisTemplate.opsForStream()).thenThrow(new RuntimeException("Connection error"));

        List<LocationPoint> locations = redisStreamService.readLocationsByTimeRange(
                "9140",
                "2024-05-12T14:00:00Z",
                "2024-05-12T14:02:00Z"
        );

        assertTrue(locations.isEmpty());
    }
}
