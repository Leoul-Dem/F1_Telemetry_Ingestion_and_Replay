package com.f1replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarData {
    private int sessionKey;
    private int driverNumber;
    private String timestamp;
    private int speed;
    private int rpm;
    private int gear;
    private int throttle;
    private int brake;
}
