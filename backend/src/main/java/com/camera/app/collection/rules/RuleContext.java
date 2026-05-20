package com.camera.app.collection.rules;

import com.camera.app.collection.plugin.ProbeResult;
import com.camera.app.collection.entity.ProbeType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Builder
public class RuleContext {
    private final Long assetId;
    private final List<ProbeResult> probeResults;

    // Convenience aggregations (built by RuleContextFactory)
    private final Set<Integer> openPorts;
    private final Set<String> protocols;

    private final boolean rtspDetected;
    private final boolean onvifDetected;
    private final String onvifManufacturer;
    private final String onvifModel;
    private final String snmpSysDescr;
    private final String snmpSysName;
    private final String sshBanner;
    private final String telnetBanner;
    private final List<String> webTitles;
    private final List<String> allTags;

    public static RuleContext from(Long assetId, List<ProbeResult> results) {
        Set<Integer> openPorts = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.PORT_SCAN && r.isPortOpen())
                .map(ProbeResult::getTargetPort)
                .collect(Collectors.toSet());

        Set<String> protocols = results.stream()
                .filter(r -> r.getApplicationProtocol() != null && r.isSuccess())
                .map(ProbeResult::getApplicationProtocol)
                .collect(Collectors.toSet());

        boolean rtspDetected = results.stream()
                .anyMatch(r -> r.getProbeType() == ProbeType.RTSP_PROBE && r.isSuccess());

        boolean onvifDetected = results.stream()
                .anyMatch(r -> r.getProbeType() == ProbeType.ONVIF_PROBE && r.isSuccess());

        String onvifManufacturer = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.ONVIF_PROBE && r.getManufacturer() != null)
                .map(ProbeResult::getManufacturer).findFirst().orElse(null);

        String onvifModel = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.ONVIF_PROBE && r.getModel() != null)
                .map(ProbeResult::getModel).findFirst().orElse(null);

        String snmpSysDescr = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.SNMP_PROBE && r.isSuccess() && r.getServiceBanner() != null)
                .map(ProbeResult::getServiceBanner).findFirst().orElse(null);

        String snmpSysName = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.SNMP_PROBE && r.isSuccess() && r.getVendorHint() != null)
                .map(ProbeResult::getVendorHint).findFirst().orElse(null);

        String sshBanner = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.SSH_BANNER && r.isSuccess() && r.getServiceBanner() != null)
                .map(ProbeResult::getServiceBanner).findFirst().orElse(null);

        String telnetBanner = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.TELNET_BANNER && r.isSuccess() && r.getServiceBanner() != null)
                .map(ProbeResult::getServiceBanner).findFirst().orElse(null);

        List<String> webTitles = results.stream()
                .filter(r -> r.getWebTitle() != null && !r.getWebTitle().isBlank())
                .map(ProbeResult::getWebTitle)
                .collect(Collectors.toList());

        List<String> allTags = results.stream()
                .flatMap(r -> r.getTags().stream())
                .collect(Collectors.toList());

        return RuleContext.builder()
                .assetId(assetId)
                .probeResults(results)
                .openPorts(openPorts)
                .protocols(protocols)
                .rtspDetected(rtspDetected)
                .onvifDetected(onvifDetected)
                .onvifManufacturer(onvifManufacturer)
                .onvifModel(onvifModel)
                .snmpSysDescr(snmpSysDescr)
                .snmpSysName(snmpSysName)
                .sshBanner(sshBanner)
                .telnetBanner(telnetBanner)
                .webTitles(webTitles)
                .allTags(allTags)
                .build();
    }
}
