package com.camera.app.alert.entity;

public enum AlertSeverity {
    CRITICAL, HIGH, MEDIUM, LOW;

    /** Score contribution when this severity is active (NEW / CONFIRMED). */
    public int weight() {
        return switch (this) {
            case CRITICAL -> 40;
            case HIGH     -> 25;
            case MEDIUM   -> 10;
            case LOW      ->  5;
        };
    }
}
