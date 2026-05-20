package com.camera.app.collection.plugin.impl;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.plugin.ProbeContext;
import com.camera.app.collection.plugin.ProbePlugin;
import com.camera.app.collection.plugin.ProbeResult;
import com.camera.app.collection.probe.LightweightProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PortProbePlugin implements ProbePlugin {

    private final LightweightProbeService lws;

    @Override
    public String getName() { return "port-probe"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.PORT_SCAN; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        List<ProbeResult> results = new ArrayList<>();
        LocalDateTime ts = LocalDateTime.now();

        for (int port : ctx.ports()) {
            LightweightProbeService.PortResult pr = lws.probePort(ctx.host(), port, ctx.timeoutMs());
            results.add(ProbeResult.builder()
                    .pluginName(getName())
                    .probeType(ProbeType.PORT_SCAN)
                    .assetId(ctx.assetId())
                    .taskId(ctx.taskId())
                    .targetHost(ctx.host())
                    .targetPort(port)
                    .transportProtocol("TCP")
                    .applicationProtocol(pr.open() ? guessProtocol(port) : null)
                    .success(pr.open())
                    .portOpen(pr.open())
                    .confidence(new BigDecimal("0.990"))
                    .rawData(pr.open() ? (pr.banner() != null ? "Banner: " + pr.banner() : "Port open") : "Port closed/unreachable")
                    .parsedData(String.format("{\"port\":%d,\"open\":%b}", port, pr.open()))
                    .serviceBanner(pr.banner())
                    .collectedAt(ts)
                    .build());
        }
        return results;
    }

    private String guessProtocol(int port) {
        return switch (port) {
            case 80, 8000, 8080, 8888 -> "HTTP";
            case 443, 8443 -> "HTTPS";
            case 554, 8554 -> "RTSP";
            case 22 -> "SSH";
            case 23 -> "TELNET";
            default -> "TCP";
        };
    }
}
