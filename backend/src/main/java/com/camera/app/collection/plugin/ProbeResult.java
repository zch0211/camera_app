package com.camera.app.collection.plugin;

import com.camera.app.collection.entity.ProbeType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProbeResult {

    // Identity
    private final String pluginName;
    private final ProbeType probeType;
    private final Long assetId;
    private final Long taskId;
    private final LocalDateTime collectedAt;

    // Target
    private final String targetHost;
    private final Integer targetPort;
    @Builder.Default private final String transportProtocol = "TCP";
    private final String applicationProtocol;

    // Outcome
    private final boolean success;
    @Builder.Default private final BigDecimal confidence = BigDecimal.ONE;
    private final String rawData;
    private final String parsedData;   // JSON string
    private final String errorMessage;

    // Port status (meaningful for PORT_SCAN type)
    private final boolean portOpen;

    // Extracted semantic fields
    private final String webTitle;
    private final String serverHeader;
    private final String vendorHint;
    private final String serviceBanner;
    private final String manufacturer;
    private final String model;
    private final String firmwareVersion;
    private final String serialNumber;
    private final String macAddress;

    // Tagging for rules engine
    @Builder.Default private final List<String> tags = List.of();
}
