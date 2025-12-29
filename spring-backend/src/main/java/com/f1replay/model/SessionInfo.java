package com.f1replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    private String sessionKey;
    private String name;
    private String dateStart;
    private String dateEnd;
    private Long durationMs;
    private Integer locationCount;
    private Integer carDataCount;
}