package com.f1replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryBatch {
    private String batchTimestamp;
    private List<LocationPoint> locations;
    private List<CarData> carData;
}
