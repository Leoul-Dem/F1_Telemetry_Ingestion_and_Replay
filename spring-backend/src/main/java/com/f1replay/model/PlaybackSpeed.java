package com.f1replay.model;

public enum PlaybackSpeed {
    NORMAL(1.0),
    DOUBLE(2.0),
    FAST(5.0),
    SUPER_FAST(10.0);

    private final double multiplier;

    PlaybackSpeed(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public static PlaybackSpeed fromMultiplier(double multiplier) {
        for (PlaybackSpeed speed : values()) {
            if (speed.multiplier == multiplier) {
                return speed;
            }
        }
        throw new IllegalArgumentException("Invalid speed multiplier: " + multiplier);
    }
}

