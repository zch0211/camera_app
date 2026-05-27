package com.camera.app.discovery.entity;

import java.util.List;

public enum DiscoveryPreset {
    CAMERA_DISCOVERY,
    NVR_DISCOVERY,
    ROUTER_DISCOVERY,
    FULL_DISCOVERY;

    public List<Integer> defaultPorts() {
        return switch (this) {
            case CAMERA_DISCOVERY -> List.of(80, 443, 554, 8080, 8443, 8554, 10554, 8000, 8001);
            case NVR_DISCOVERY    -> List.of(80, 443, 554, 8080, 8000, 8200, 37777, 34567);
            case ROUTER_DISCOVERY -> List.of(80, 443, 8080, 22, 23, 161);
            case FULL_DISCOVERY   -> List.of(80, 443, 554, 8080, 8443, 8554, 10554,
                                             8000, 8001, 37777, 34567, 161, 22, 23);
        };
    }
}
