package com.camera.app.collection.plugin.impl;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.plugin.ProbeContext;
import com.camera.app.collection.plugin.ProbePlugin;
import com.camera.app.collection.plugin.ProbeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Component
public class RtspProbePlugin implements ProbePlugin {

    private static final List<Integer> RTSP_PORTS = List.of(554, 8554, 8557, 10554);
    private static final Pattern SERVER_PATTERN = Pattern.compile("(?i)^Server:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern PUBLIC_PATTERN = Pattern.compile("(?i)^Public:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern CONTENT_BASE_PATTERN = Pattern.compile("(?i)^Content-Base:\\s*(.+)$", Pattern.MULTILINE);

    @Override
    public String getName() { return "rtsp-probe"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.RTSP_PROBE; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        List<ProbeResult> results = new ArrayList<>();
        LocalDateTime ts = LocalDateTime.now();

        Set<Integer> portsToProbe = new LinkedHashSet<>(RTSP_PORTS);
        ctx.ports().stream().filter(p -> p == 554 || p > 8000).forEach(portsToProbe::add);

        for (int port : portsToProbe) {
            results.add(probeRtsp(ctx.host(), port, ctx.timeoutMs(), ctx, ts));
        }
        return results;
    }

    private ProbeResult probeRtsp(String host, int port, int timeoutMs,
                                   ProbeContext ctx, LocalDateTime ts) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // Send OPTIONS
            String optionsReq = "OPTIONS rtsp://" + host + ":" + port + "/ RTSP/1.0\r\n" +
                    "CSeq: 1\r\nUser-Agent: CameraScanner/1.0\r\n\r\n";
            pw.print(optionsReq);
            pw.flush();

            String response = readRtspResponse(reader);
            if (response == null || !response.startsWith("RTSP/")) {
                return failResult(ctx, port, ts, "Non-RTSP response");
            }

            String serverHint = extractGroup(SERVER_PATTERN, response);
            String publicMethods = extractGroup(PUBLIC_PATTERN, response);

            // Send DESCRIBE (just to probe capabilities, not to get stream)
            String describeReq = "DESCRIBE rtsp://" + host + ":" + port + "/ RTSP/1.0\r\n" +
                    "CSeq: 2\r\nUser-Agent: CameraScanner/1.0\r\nAccept: application/sdp\r\n\r\n";
            pw.print(describeReq);
            pw.flush();

            String describeResp = readRtspResponse(reader);
            String describeStatus = describeResp != null && describeResp.length() > 9
                    ? describeResp.substring(0, Math.min(describeResp.indexOf('\r') < 0 ? 50 : describeResp.indexOf('\r'), 50))
                    : "no describe response";

            List<String> tags = new ArrayList<>();
            tags.add("rtsp-capable");
            if (serverHint != null) {
                String sl = serverHint.toLowerCase();
                if (sl.contains("hikvision") || sl.contains("dahua") || sl.contains("axis")
                        || sl.contains("reolink") || sl.contains("camera")) tags.add("camera-like");
            }

            String rawSummary = "RTSP OPTIONS succeeded. Server=" + serverHint
                    + " Public=" + publicMethods + " DESCRIBE=" + describeStatus;

            String parsedJson = String.format(
                    "{\"port\":%d,\"server\":%s,\"publicMethods\":%s,\"describeStatus\":%s}",
                    port,
                    serverHint != null ? "\"" + serverHint + "\"" : "null",
                    publicMethods != null ? "\"" + publicMethods + "\"" : "null",
                    "\"" + describeStatus + "\"");

            return ProbeResult.builder()
                    .pluginName(getName())
                    .probeType(ProbeType.RTSP_PROBE)
                    .assetId(ctx.assetId()).taskId(ctx.taskId())
                    .targetHost(host).targetPort(port)
                    .transportProtocol("TCP").applicationProtocol("RTSP")
                    .success(true).portOpen(true)
                    .confidence(new BigDecimal("0.950"))
                    .rawData(rawSummary).parsedData(parsedJson)
                    .serverHeader(serverHint).vendorHint(serverHint)
                    .serviceBanner("RTSP server: " + serverHint)
                    .tags(tags).collectedAt(ts)
                    .build();

        } catch (Exception e) {
            log.debug("RTSP probe {}:{} failed: {}", host, port, e.getMessage());
            return failResult(ctx, port, ts, e.getMessage());
        }
    }

    private String readRtspResponse(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        int emptyLines = 0;
        int maxLines = 30;
        while (maxLines-- > 0 && (line = reader.readLine()) != null) {
            sb.append(line).append("\r\n");
            if (line.isEmpty()) {
                if (++emptyLines >= 1) break;
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String extractGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private ProbeResult failResult(ProbeContext ctx, int port, LocalDateTime ts, String error) {
        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.RTSP_PROBE)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(ctx.host()).targetPort(port)
                .transportProtocol("TCP").applicationProtocol("RTSP")
                .success(false).portOpen(false)
                .confidence(BigDecimal.ZERO)
                .errorMessage(error).collectedAt(ts).build();
    }
}
