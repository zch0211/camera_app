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
public class TelnetBannerPlugin implements ProbePlugin {

    private static final int TELNET_PORT = 23;
    private static final int READ_BYTES = 512;
    private static final int IAC = 0xFF;

    @Override
    public String getName() { return "telnet-banner"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.TELNET_BANNER; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        LocalDateTime ts = LocalDateTime.now();
        return List.of(probeTelnet(ctx.host(), TELNET_PORT, ctx.timeoutMs(), ctx, ts));
    }

    private ProbeResult probeTelnet(String host, int port, int timeoutMs,
                                     ProbeContext ctx, LocalDateTime ts) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            byte[] buf = new byte[READ_BYTES];
            int n = socket.getInputStream().read(buf);
            if (n <= 0) return failResult(ctx, port, ts, "No data received");

            // Strip IAC (0xFF) negotiation bytes: IAC <cmd> <opt> = 3 bytes
            StringBuilder text = new StringBuilder();
            int i = 0;
            while (i < n) {
                int b = buf[i] & 0xFF;
                if (b == IAC && i + 2 < n) {
                    i += 3;  // skip IAC + cmd + option
                } else if (b >= 32 && b < 127) {
                    text.append((char) b);
                    i++;
                } else {
                    i++;
                }
            }
            String banner = text.toString().trim();
            if (banner.isBlank()) banner = "(binary/IAC-only response)";

            String parsedJson = String.format(
                    "{\"port\":%d,\"banner\":%s}",
                    port, "\"" + banner.replace("\"", "'") + "\"");

            return ProbeResult.builder()
                    .pluginName(getName()).probeType(ProbeType.TELNET_BANNER)
                    .assetId(ctx.assetId()).taskId(ctx.taskId())
                    .targetHost(host).targetPort(port)
                    .transportProtocol("TCP").applicationProtocol("TELNET")
                    .success(true).portOpen(true)
                    .confidence(new BigDecimal("0.950"))
                    .rawData(banner).parsedData(parsedJson)
                    .serviceBanner(banner)
                    .collectedAt(ts).build();

        } catch (Exception e) {
            log.debug("Telnet banner {}:{} failed: {}", host, port, e.getMessage());
            return failResult(ctx, port, ts, e.getMessage());
        }
    }

    private ProbeResult failResult(ProbeContext ctx, int port, LocalDateTime ts, String error) {
        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.TELNET_BANNER)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(ctx.host()).targetPort(port)
                .transportProtocol("TCP").applicationProtocol("TELNET")
                .success(false).portOpen(false)
                .confidence(BigDecimal.ZERO).errorMessage(error).collectedAt(ts).build();
    }
}
