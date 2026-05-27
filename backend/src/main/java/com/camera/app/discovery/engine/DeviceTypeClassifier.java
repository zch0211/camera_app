package com.camera.app.discovery.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rule-based device type classification from open ports and banner hints.
 * Returns the most likely device type and a rough confidence score.
 * No vulnerability probing — defensive identification only.
 */
public final class DeviceTypeClassifier {

    private DeviceTypeClassifier() {}

    public record ClassificationResult(String deviceType, BigDecimal confidence) {}

    public static ClassificationResult classify(List<Integer> openPorts, String vendorHint, String banner) {
        if (openPorts == null || openPorts.isEmpty()) {
            return new ClassificationResult("UNKNOWN", BigDecimal.valueOf(0.1));
        }

        int score = 0;
        String bestType = "OTHER";

        // Dahua-specific ports → NVR/CAMERA
        if (openPorts.contains(37777) || openPorts.contains(34567)) {
            score += 70;
            bestType = openPorts.contains(554) ? "CAMERA" : "NVR";
        }
        // Hikvision 8000 control port
        if (openPorts.contains(8000)) {
            score += 40;
            if (score < 70) bestType = openPorts.contains(554) ? "CAMERA" : "NVR";
        }
        // RTSP ports
        if (openPorts.contains(554) || openPorts.contains(8554) || openPorts.contains(10554)) {
            score += 30;
            if (bestType.equals("OTHER")) bestType = "CAMERA";
        }
        // SNMP → ROUTER or NVR
        if (openPorts.contains(161)) {
            score += 20;
            if (bestType.equals("OTHER")) bestType = "ROUTER";
        }
        // Telnet/SSH without RTSP → ROUTER
        if ((openPorts.contains(22) || openPorts.contains(23)) && !openPorts.contains(554)) {
            score += 15;
            if (bestType.equals("OTHER")) bestType = "ROUTER";
        }
        // Plain HTTP/HTTPS only → could be anything, keep OTHER with low score
        if (openPorts.contains(80) || openPorts.contains(443) || openPorts.contains(8080)) {
            score += 10;
        }

        // Vendor hint overrides
        if (vendorHint != null) {
            String hint = vendorHint.toLowerCase();
            if (hint.contains("hikvision") || hint.contains("dahua") ||
                hint.contains("reolink") || hint.contains("axis")) {
                score = Math.max(score, 65);
                if (bestType.equals("OTHER") || bestType.equals("ROUTER")) bestType = "CAMERA";
            }
            if (hint.contains("nvr") || hint.contains("dvr")) {
                score = Math.max(score, 65);
                bestType = "NVR";
            }
            if (hint.contains("router") || hint.contains("gateway") ||
                hint.contains("cisco") || hint.contains("huawei") || hint.contains("mikrotik")) {
                score = Math.max(score, 60);
                bestType = "ROUTER";
            }
        }

        if (banner != null) {
            String b = banner.toLowerCase();
            if (b.contains("rtsp") || b.contains("camera") || b.contains("ipc")) {
                score = Math.max(score, 55);
                if (bestType.equals("OTHER")) bestType = "CAMERA";
            }
        }

        double conf = Math.min(score / 100.0, 0.95);
        return new ClassificationResult(bestType, BigDecimal.valueOf(conf).setScale(3, java.math.RoundingMode.HALF_UP));
    }

    public static List<String> inferProtocols(List<Integer> openPorts) {
        if (openPorts == null) return List.of();
        List<String> protocols = new java.util.ArrayList<>();
        if (openPorts.contains(554) || openPorts.contains(8554) || openPorts.contains(10554))
            protocols.add("RTSP");
        if (openPorts.contains(80) || openPorts.contains(8080))
            protocols.add("HTTP");
        if (openPorts.contains(443) || openPorts.contains(8443))
            protocols.add("HTTPS");
        if (openPorts.contains(22))
            protocols.add("SSH");
        if (openPorts.contains(23))
            protocols.add("TELNET");
        if (openPorts.contains(161))
            protocols.add("SNMP");
        if (openPorts.contains(8000) || openPorts.contains(37777) || openPorts.contains(34567))
            protocols.add("PROPRIETARY");
        return protocols;
    }
}
