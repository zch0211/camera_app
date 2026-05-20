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
public class HttpFingerprintPlugin implements ProbePlugin {

    private final LightweightProbeService lws;

    @Override
    public String getName() { return "http-fingerprint"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.HTTP_TITLE; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        List<ProbeResult> results = new ArrayList<>();
        LocalDateTime ts = LocalDateTime.now();

        for (int port : ctx.ports()) {
            // Try HTTP
            LightweightProbeService.HttpResult http = lws.probeHttp(ctx.host(), port, ctx.timeoutMs());
            results.add(toProbeResult(http, ctx, ts, "HTTP"));

            // Try HTTPS on likely SSL ports
            if (isSslPort(port)) {
                LightweightProbeService.HttpResult https = lws.probeHttps(ctx.host(), port, ctx.timeoutMs());
                results.add(toProbeResult(https, ctx, ts, "HTTPS"));
            }
        }
        return results;
    }

    private ProbeResult toProbeResult(LightweightProbeService.HttpResult hr,
                                       ProbeContext ctx, LocalDateTime ts, String scheme) {
        String parsedData = String.format(
                "{\"port\":%d,\"protocol\":\"%s\",\"title\":%s,\"server\":%s}",
                hr.port(), hr.protocol(),
                hr.title() != null ? "\"" + hr.title().replace("\"", "'") + "\"" : "null",
                hr.serverHeader() != null ? "\"" + hr.serverHeader().replace("\"", "'") + "\"" : "null");

        return ProbeResult.builder()
                .pluginName(getName())
                .probeType(ProbeType.HTTP_TITLE)
                .assetId(ctx.assetId())
                .taskId(ctx.taskId())
                .targetHost(ctx.host())
                .targetPort(hr.port())
                .transportProtocol("TCP")
                .applicationProtocol(hr.success() ? scheme : null)
                .success(hr.success())
                .confidence(new BigDecimal("0.950"))
                .rawData(hr.success() ? "Title: " + hr.title() + "  Server: " + hr.serverHeader() : null)
                .parsedData(parsedData)
                .errorMessage(hr.error())
                .webTitle(hr.title())
                .serverHeader(hr.serverHeader())
                .vendorHint(hr.serverHeader())
                .collectedAt(ts)
                .build();
    }

    private boolean isSslPort(int port) {
        return port == 443 || port == 8443 || port == 8888;
    }
}
