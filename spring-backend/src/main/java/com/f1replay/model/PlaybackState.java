package com.f1replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackState {
    private String sessionKey;
    private PlaybackStatus status;
    private String currentTime;
    private String startTime;
    private String endTime;
    private PlaybackSpeed speed;
    private long durationMs;
    private long elapsedMs;
}
