package com.camera.app.discovery.engine;

import java.util.List;

/** Raw result from scanning a single host. */
public record ScanHostResult(
        String ip,
        boolean alive,
        List<Integer> openPorts,
        String banner,
        String vendorHint
) {}
