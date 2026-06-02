package com.camera.app.discovery.entity;

/**
 * Dual-level discovery result classification.
 *
 * ALIVE    - Host responded (ICMP or TCP RST/handshake) but no high-value port was open
 *            and no vendor hint was detected. Useful for network inventory.
 * CANDIDATE - Host has at least one open high-value port or a vendor hint signal.
 *            More likely to be a manageable device (camera, NVR, router).
 */
public enum DiscoveryLevel {
    ALIVE,
    CANDIDATE
}
