package com.f1replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationPoint {
    private int sessionKey;
    private int driverNumber;
    private String timestamp;
    private double x;
    private double y;
}
