package com.camera.app.discovery.dto;

import com.camera.app.discovery.entity.DiscoveryPreset;
import com.camera.app.discovery.entity.DiscoveryTaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DiscoveryTaskCreateRequest {

    @NotNull
    private DiscoveryTaskType taskType;

    @NotNull
    private DiscoveryPreset preset = DiscoveryPreset.FULL_DISCOVERY;

    @NotBlank
    private String targetScope;

    private List<Integer> ports;

    private Integer timeoutMillis;

    /** PASSIVE_SNIFF only: duration in seconds */
    private Integer sniffDurationSeconds;

    /** PASSIVE_SNIFF only: network interface name, e.g. "eth0" or "本地连接" */
    private String networkInterface;
}
