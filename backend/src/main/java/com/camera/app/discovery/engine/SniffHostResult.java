package com.camera.app.discovery.engine;

import java.util.List;

/** Raw result from passive sniffing of a single discovered host. */
public record SniffHostResult(
        String ip,
        String mac,
        String hostname,
        String vendorHint,
        List<String> protocols,
        String rawEvidence
) {}
