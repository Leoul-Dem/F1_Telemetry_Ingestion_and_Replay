package com.f1replay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "replay")
public class ReplayProperties {

    private Batch batch = new Batch();
    private Buffer buffer = new Buffer();
    private int fps = 30;
    private double frameIntervalMs = 33.333;
    private int stateRetentionMinutes = 5;
    private List<SessionConfig> sessions = new ArrayList<>();

    @Data
    public static class Batch {
        private int intervalMs = 100;
    }

    @Data
    public static class Buffer {
        private int durationSeconds = 30;
    }

    @Data
    public static class SessionConfig {
        private String key;
        private String name;
        private String dateStart;
        private String dateEnd;
    }
}