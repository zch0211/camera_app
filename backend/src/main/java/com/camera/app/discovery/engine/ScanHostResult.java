package com.camera.app.discovery.engine;

import java.util.List;

/** Raw result from scanning a single host. */
public record ScanHostResult(
        String ip,
        boolean icmpAlive,
        List<Integer> openPorts,
        String banner,
        String vendorHint
) {
    /** True if the host responded via ICMP ping OR at least one TCP port is open. */
    public boolean alive() {
        return icmpAlive || !openPorts.isEmpty();
    }
}
