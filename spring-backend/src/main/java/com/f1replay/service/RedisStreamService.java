package com.f1replay.service;

import com.f1replay.model.CarData;
import com.f1replay.model.LocationPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOCATION_STREAM_PREFIX = "telemetry:location:";
    private static final String CARDATA_STREAM_PREFIX = "telemetry:cardata:";

    /**
     * Get the location stream key for a session
     */
    public String getLocationStreamKey(String sessionKey) {
        return LOCATION_STREAM_PREFIX + sessionKey;
    }

    /**
     * Get the car data stream key for a session
     */
    public String getCarDataStreamKey(String sessionKey) {
        return CARDATA_STREAM_PREFIX + sessionKey;
    }

    /**
     * Get the total count of entries in a stream
     */
    public Long getStreamLength(String streamKey) {
        try {
            return redisTemplate.opsForStream().size(streamKey);
        } catch (Exception e) {
            log.error("Error getting stream length for {}: {}", streamKey, e.getMessage());
            return 0L;
        }
    }

    /**
     * Read location data from Redis stream within a time range.
     * Uses XRANGE to fetch entries between start and end timestamps.
     */
    public List<LocationPoint> readLocationsByTimeRange(String sessionKey, String startTime, String endTime) {
        String streamKey = getLocationStreamKey(sessionKey);
        List<MapRecord<String, Object, Object>> records = readStreamRange(streamKey, startTime, endTime);

        List<LocationPoint> locations = new ArrayList<>();
        for (MapRecord<String, Object, Object> record : records) {
            try {
                LocationPoint point = parseLocationPoint(record.getValue(), sessionKey);
                if (point != null) {
                    locations.add(point);
                }
            } catch (Exception e) {
                log.warn("Failed to parse location record: {}", e.getMessage());
            }
        }

        log.debug("Read {} location points from {} between {} and {}",
                locations.size(), streamKey, startTime, endTime);
        return locations;
    }

    /**
     * Read car data from Redis stream within a time range.
     */
    public List<CarData> readCarDataByTimeRange(String sessionKey, String startTime, String endTime) {
        String streamKey = getCarDataStreamKey(sessionKey);
        List<MapRecord<String, Object, Object>> records = readStreamRange(streamKey, startTime, endTime);

        List<CarData> carDataList = new ArrayList<>();
        for (MapRecord<String, Object, Object> record : records) {
            try {
                CarData carData = parseCarData(record.getValue(), sessionKey);
                if (carData != null) {
                    carDataList.add(carData);
                }
            } catch (Exception e) {
                log.warn("Failed to parse car data record: {}", e.getMessage());
            }
        }

        log.debug("Read {} car data points from {} between {} and {}",
                carDataList.size(), streamKey, startTime, endTime);
        return carDataList;
    }

    /**
     * Read all entries from a stream (use with caution - for metadata/testing only)
     */
    public List<MapRecord<String, Object, Object>> readAllFromStream(String streamKey, int limit) {
        try {
            return redisTemplate.opsForStream()
                    .read(StreamReadOptions.empty().count(limit),
                            StreamOffset.fromStart(streamKey));
        } catch (Exception e) {
            log.error("Error reading from stream {}: {}", streamKey, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Read stream entries within a range using Redis XRANGE
     */
    private List<MapRecord<String, Object, Object>> readStreamRange(String streamKey, String startTime, String endTime) {
        try {
            // Redis streams use "-" for minimum and "+" for maximum
            // We use the full range and filter by timestamp field in the data
            List<MapRecord<String, Object, Object>> allRecords = redisTemplate.opsForStream()
                    .range(streamKey, Range.unbounded());

            if (allRecords == null || allRecords.isEmpty()) {
                return Collections.emptyList();
            }

            // Filter by timestamp field in the record data
            Instant start = parseTimestamp(startTime);
            Instant end = parseTimestamp(endTime);

            if (start == null || end == null) {
                log.warn("Invalid time range: {} - {}", startTime, endTime);
                return allRecords; // Return all if we can't parse times
            }

            return allRecords.stream()
                    .filter(record -> {
                        String timestamp = getStringValue(record.getValue(), "timestamp");
                        if (timestamp == null) return false;
                        Instant recordTime = parseTimestamp(timestamp);
                        if (recordTime == null) return false;
                        return !recordTime.isBefore(start) && !recordTime.isAfter(end);
                    })
                    .toList();

        } catch (Exception e) {
            log.error("Error reading stream range from {}: {}", streamKey, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parse a LocationPoint from Redis stream record values
     */
    private LocationPoint parseLocationPoint(Map<Object, Object> values, String sessionKey) {
        return LocationPoint.builder()
                .sessionKey(Integer.parseInt(sessionKey))
                .driverNumber(getIntValue(values, "driver_number"))
                .timestamp(getStringValue(values, "timestamp"))
                .x(getDoubleValue(values, "x"))
                .y(getDoubleValue(values, "y"))
                .build();
    }

    /**
     * Parse CarData from Redis stream record values
     */
    private CarData parseCarData(Map<Object, Object> values, String sessionKey) {
        return CarData.builder()
                .sessionKey(Integer.parseInt(sessionKey))
                .driverNumber(getIntValue(values, "driver_number"))
                .timestamp(getStringValue(values, "timestamp"))
                .speed(getIntValue(values, "speed"))
                .rpm(getIntValue(values, "rpm"))
                .gear(getIntValue(values, "gear"))
                .throttle(getIntValue(values, "throttle"))
                .brake(getIntValue(values, "brake"))
                .build();
    }

    /**
     * Safely get a String value from record map
     */
    private String getStringValue(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Safely get an int value from record map
     */
    private int getIntValue(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Safely get a double value from record map
     */
    private double getDoubleValue(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Parse ISO timestamp string to Instant
     */
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse timestamp: {}", timestamp);
            return null;
        }
    }

    /**
     * Check if a stream exists and has data
     */
    public boolean streamExists(String streamKey) {
        try {
            Long length = redisTemplate.opsForStream().size(streamKey);
            return length != null && length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the first timestamp in a stream
     */
    public String getFirstTimestamp(String streamKey) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .range(streamKey, Range.unbounded(), org.springframework.data.redis.connection.Limit.limit().count(1));
            if (records != null && !records.isEmpty()) {
                return getStringValue(records.get(0).getValue(), "timestamp");
            }
        } catch (Exception e) {
            log.error("Error getting first timestamp from {}: {}", streamKey, e.getMessage());
        }
        return null;
    }

    /**
     * Get the last timestamp in a stream
     */
    public String getLastTimestamp(String streamKey) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .reverseRange(streamKey, Range.unbounded(), org.springframework.data.redis.connection.Limit.limit().count(1));
            if (records != null && !records.isEmpty()) {
                return getStringValue(records.get(0).getValue(), "timestamp");
            }
        } catch (Exception e) {
            log.error("Error getting last timestamp from {}: {}", streamKey, e.getMessage());
        }
        return null;
    }
}
