package com.camera.app.discovery.entity;

import java.util.List;

public enum DiscoveryPreset {
    CAMERA_DISCOVERY,
    NVR_DISCOVERY,
    ROUTER_DISCOVERY,
    FULL_DISCOVERY;

    /** Minimal high-value port set for fast discovery — not deep scanning. */
    public List<Integer> defaultPorts() {
        return switch (this) {
            case CAMERA_DISCOVERY -> List.of(80, 443, 8080, 8000, 554, 8554);
            case NVR_DISCOVERY    -> List.of(80, 443, 8080, 554, 8000, 37777, 34567);
            case ROUTER_DISCOVERY -> List.of(80, 443, 8080, 8443, 22, 23, 161);
            case FULL_DISCOVERY   -> List.of(80, 443, 8080, 8443, 554, 8554, 8000, 22, 23, 161);
        };
    }
}
