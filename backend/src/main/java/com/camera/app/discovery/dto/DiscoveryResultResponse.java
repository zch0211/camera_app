package com.camera.app.discovery.dto;

import com.camera.app.discovery.entity.DiscoveryResult;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class DiscoveryResultResponse {

    private final Long id;
    private final Long taskId;
    private final String ip;
    private final String mac;
    private final String hostname;
    private final String vendorHint;
    private final String openPorts;
    private final String protocols;
    private final String deviceTypeCandidate;
    private final BigDecimal confidence;
    private final String sourceType;
    private final String discoveryLevel;
    private final String rawEvidence;
    private final LocalDateTime firstSeenAt;
    private final LocalDateTime lastSeenAt;
    private final Long linkedAssetId;
    private final boolean managed;
    private final LocalDateTime createdAt;

    public DiscoveryResultResponse(DiscoveryResult r) {
        this.id = r.getId();
        this.taskId = r.getTaskId();
        this.ip = r.getIp();
        this.mac = r.getMac();
        this.hostname = r.getHostname();
        this.vendorHint = r.getVendorHint();
        this.openPorts = r.getOpenPorts();
        this.protocols = r.getProtocols();
        this.deviceTypeCandidate = r.getDeviceTypeCandidate();
        this.confidence = r.getConfidence();
        this.sourceType = r.getSourceType().name();
        this.discoveryLevel = r.getDiscoveryLevel().name();
        this.rawEvidence = r.getRawEvidence();
        this.firstSeenAt = r.getFirstSeenAt();
        this.lastSeenAt = r.getLastSeenAt();
        this.linkedAssetId = r.getLinkedAssetId();
        this.managed = r.isManaged();
        this.createdAt = r.getCreatedAt();
    }
}
