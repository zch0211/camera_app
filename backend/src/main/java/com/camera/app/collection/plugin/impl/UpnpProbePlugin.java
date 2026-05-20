package com.camera.app.collection.plugin.impl;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.plugin.ProbeContext;
import com.camera.app.collection.plugin.ProbePlugin;
import com.camera.app.collection.plugin.ProbeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Component
public class UpnpProbePlugin implements ProbePlugin {

    private static final String MULTICAST_ADDR = "239.255.255.250";
    private static final int    MULTICAST_PORT = 1900;
    private static final int    RESPONSE_TIMEOUT_MS = 3000;
    private static final String MSEARCH =
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: upnp:rootdevice\r\n\r\n";

    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?i)^LOCATION:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern SERVER_PATTERN   = Pattern.compile("(?i)^SERVER:\\s*(.+)$",   Pattern.MULTILINE);
    private static final Pattern USN_PATTERN      = Pattern.compile("(?i)^USN:\\s*(.+)$",      Pattern.MULTILINE);

    @Override
    public String getName() { return "upnp-probe"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.UPNP_PROBE; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        LocalDateTime ts = LocalDateTime.now();
        List<ProbeResult> results = new ArrayList<>();

        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(RESPONSE_TIMEOUT_MS);

            byte[] data = MSEARCH.getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDR);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, MULTICAST_PORT);
            socket.send(packet);

            byte[] buf = new byte[4096];
            long deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket resp = new DatagramPacket(buf, buf.length);
                    socket.receive(resp);
                    String response = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);
                    String fromIp = resp.getAddress().getHostAddress();
                    results.add(parseUpnpResponse(response, fromIp, ctx, ts));
                } catch (SocketTimeoutException e) { break; }
            }
            socket.close();

        } catch (Exception e) {
            log.debug("UPnP probe failed: {}", e.getMessage());
            results.add(ProbeResult.builder()
                    .pluginName(getName()).probeType(ProbeType.UPNP_PROBE)
                    .assetId(ctx.assetId()).taskId(ctx.taskId())
                    .targetHost(ctx.host()).targetPort(MULTICAST_PORT)
                    .transportProtocol("UDP").applicationProtocol("UPnP")
                    .success(false).portOpen(false)
                    .confidence(BigDecimal.ZERO).errorMessage(e.getMessage()).collectedAt(ts).build());
        }

        return results.isEmpty() ? List.of(failResult(ctx, ts)) : results;
    }

    private ProbeResult parseUpnpResponse(String response, String fromIp,
                                           ProbeContext ctx, LocalDateTime ts) {
        String location = extractGroup(LOCATION_PATTERN, response);
        String server   = extractGroup(SERVER_PATTERN,   response);
        String usn      = extractGroup(USN_PATTERN,      response);

        Map<String, String> deviceInfo = new LinkedHashMap<>();
        if (location != null) {
            deviceInfo.putAll(fetchDeviceDescription(location, ctx.timeoutMs()));
        }

        String manufacturer = deviceInfo.get("manufacturer");
        String modelName    = deviceInfo.get("modelName");
        String deviceType   = deviceInfo.get("deviceType");

        List<String> tags = new ArrayList<>();
        if (deviceType != null) {
            String dt = deviceType.toLowerCase();
            if (dt.contains("camera") || dt.contains("ipcam")) tags.add("camera-like");
            else if (dt.contains("nvr") || dt.contains("mediaserver")) tags.add("nvr-like");
            else if (dt.contains("router") || dt.contains("gateway")) tags.add("router-like");
        }

        String parsedJson = String.format(
                "{\"fromIp\":\"%s\",\"location\":%s,\"server\":%s,\"manufacturer\":%s,\"modelName\":%s,\"deviceType\":%s}",
                fromIp,
                location != null ? "\"" + location + "\"" : "null",
                server   != null ? "\"" + server + "\"" : "null",
                manufacturer != null ? "\"" + manufacturer + "\"" : "null",
                modelName    != null ? "\"" + modelName + "\"" : "null",
                deviceType   != null ? "\"" + deviceType + "\"" : "null");

        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.UPNP_PROBE)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(fromIp).targetPort(MULTICAST_PORT)
                .transportProtocol("UDP").applicationProtocol("UPnP")
                .success(true).portOpen(true)
                .confidence(new BigDecimal("0.850"))
                .rawData(response.substring(0, Math.min(256, response.length())))
                .parsedData(parsedJson)
                .manufacturer(manufacturer)
                .model(modelName)
                .vendorHint(manufacturer != null ? manufacturer : server)
                .tags(tags).collectedAt(ts).build();
    }

    private Map<String, String> fetchDeviceDescription(String locationUrl, int timeoutMs) {
        Map<String, String> info = new LinkedHashMap<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(locationUrl).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.connect();
            if (conn.getResponseCode() != 200) { conn.disconnect(); return info; }

            String xml;
            try (InputStream is = conn.getInputStream()) {
                xml = new String(is.readNBytes(8192), StandardCharsets.UTF_8);
            }
            conn.disconnect();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            for (String field : List.of("manufacturer", "modelName", "modelDescription", "deviceType", "friendlyName")) {
                NodeList nl = doc.getElementsByTagNameNS("*", field);
                if (nl.getLength() == 0) nl = doc.getElementsByTagName(field);
                if (nl.getLength() > 0) info.put(field, nl.item(0).getTextContent().trim());
            }
        } catch (Exception e) {
            log.debug("UPnP device description fetch failed: {}", e.getMessage());
        }
        return info;
    }

    private String extractGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private ProbeResult failResult(ProbeContext ctx, LocalDateTime ts) {
        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.UPNP_PROBE)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(ctx.host()).targetPort(MULTICAST_PORT)
                .transportProtocol("UDP").applicationProtocol("UPnP")
                .success(false).portOpen(false)
                .confidence(BigDecimal.ZERO)
                .errorMessage("No UPnP devices responded").collectedAt(ts).build();
    }
}
