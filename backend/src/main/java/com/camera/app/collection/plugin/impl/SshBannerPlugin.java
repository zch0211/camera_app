package com.camera.app.collection.plugin.impl;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.plugin.ProbeContext;
import com.camera.app.collection.plugin.ProbePlugin;
import com.camera.app.collection.plugin.ProbeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class SshBannerPlugin implements ProbePlugin {

    private static final int SSH_PORT = 22;

    @Override
    public String getName() { return "ssh-banner"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.SSH_BANNER; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        LocalDateTime ts = LocalDateTime.now();
        return List.of(probeSsh(ctx.host(), SSH_PORT, ctx.timeoutMs(), ctx, ts));
    }

    private ProbeResult probeSsh(String host, int port, int timeoutMs,
                                  ProbeContext ctx, LocalDateTime ts) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            // SSH server sends banner immediately upon connect
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String banner = reader.readLine();

            if (banner == null || banner.isBlank()) {
                return failResult(ctx, port, ts, "Empty SSH banner");
            }

            // SSH banner format: SSH-<protoversion>-<softwareversion> [<comments>]
            String vendorHint = null;
            if (banner.startsWith("SSH-")) {
                String[] parts = banner.split("-", 3);
                if (parts.length >= 3) vendorHint = parts[2].trim();
            }

            String parsedJson = String.format(
                    "{\"port\":%d,\"banner\":%s,\"vendorHint\":%s}",
                    port,
                    "\"" + banner.replace("\"", "'") + "\"",
                    vendorHint != null ? "\"" + vendorHint + "\"" : "null");

            return ProbeResult.builder()
                    .pluginName(getName()).probeType(ProbeType.SSH_BANNER)
                    .assetId(ctx.assetId()).taskId(ctx.taskId())
                    .targetHost(host).targetPort(port)
                    .transportProtocol("TCP").applicationProtocol("SSH")
                    .success(true).portOpen(true)
                    .confidence(new BigDecimal("0.990"))
                    .rawData(banner).parsedData(parsedJson)
                    .serviceBanner(banner).vendorHint(vendorHint)
                    .collectedAt(ts).build();

        } catch (Exception e) {
            log.debug("SSH banner {}:{} failed: {}", host, port, e.getMessage());
            return failResult(ctx, port, ts, e.getMessage());
        }
    }

    private ProbeResult failResult(ProbeContext ctx, int port, LocalDateTime ts, String error) {
        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.SSH_BANNER)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(ctx.host()).targetPort(port)
                .transportProtocol("TCP").applicationProtocol("SSH")
                .success(false).portOpen(false)
                .confidence(BigDecimal.ZERO).errorMessage(error).collectedAt(ts).build();
    }
}
