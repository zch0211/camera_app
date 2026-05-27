package com.camera.app.discovery.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Active scan engine: ICMP reachability + TCP connect probe.
 * No exploitation, no credential testing, no command execution.
 * Only discovers: alive hosts, open ports, banner excerpts for device classification.
 */
@Slf4j
@Component
public class ActiveScanEngine {

    private static final int BANNER_READ_BYTES = 256;
    private static final int BANNER_SO_TIMEOUT_MS = 400;

    /**
     * Scan all IPs in the given list, probing the provided ports.
     * Uses a thread pool sized to min(16, ip count) for parallelism.
     */
    public List<ScanHostResult> scan(List<String> ips, List<Integer> ports, int timeoutMs) {
        if (ips.isEmpty()) return List.of();

        int threads = Math.min(16, ips.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<ScanHostResult>> futures = new ArrayList<>(ips.size());

        for (String ip : ips) {
            futures.add(pool.submit(() -> scanHost(ip, ports, timeoutMs)));
        }

        List<ScanHostResult> results = new ArrayList<>();
        for (Future<ScanHostResult> f : futures) {
            try {
                ScanHostResult r = f.get();
                if (r.alive()) results.add(r);
            } catch (Exception e) {
                log.debug("Scan future failed: {}", e.getMessage());
            }
        }
        pool.shutdownNow();
        return results;
    }

    private ScanHostResult scanHost(String ip, List<Integer> ports, int timeoutMs) {
        boolean alive = false;
        List<Integer> openPorts = new ArrayList<>();
        String firstBanner = null;
        String vendorHint = null;

        // 1. ICMP reachability (best-effort; may fail without admin on some OSes)
        try {
            alive = InetAddress.getByName(ip).isReachable(timeoutMs);
        } catch (Exception ignored) {}

        // 2. TCP connect probes — each open TCP port proves host is alive
        for (int port : ports) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), timeoutMs);
                alive = true;
                openPorts.add(port);

                // Try banner grab on first open port only to save time
                if (firstBanner == null) {
                    firstBanner = tryBanner(socket);
                    if (firstBanner != null) {
                        vendorHint = extractVendorHint(firstBanner);
                    }
                }
            } catch (Exception ignored) {
                // port closed or filtered — expected
            }
        }

        return new ScanHostResult(ip, alive, openPorts, firstBanner, vendorHint);
    }

    private String tryBanner(Socket socket) {
        try {
            socket.setSoTimeout(BANNER_SO_TIMEOUT_MS);
            InputStream is = socket.getInputStream();
            byte[] buf = new byte[BANNER_READ_BYTES];
            int n = is.read(buf);
            if (n > 0) return new String(buf, 0, n, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {}
        return null;
    }

    private String extractVendorHint(String banner) {
        if (banner == null) return null;
        // Look for known vendor strings in banner text
        String lower = banner.toLowerCase();
        if (lower.contains("hikvision"))  return "Hikvision";
        if (lower.contains("dahua"))      return "Dahua";
        if (lower.contains("axis"))       return "Axis";
        if (lower.contains("reolink"))    return "Reolink";
        if (lower.contains("cisco"))      return "Cisco";
        if (lower.contains("huawei"))     return "Huawei";
        if (lower.contains("mikrotik"))   return "MikroTik";
        // Return first non-empty line as a generic hint (max 64 chars)
        String firstLine = banner.split("\n")[0].trim();
        return firstLine.length() > 64 ? firstLine.substring(0, 64) : firstLine;
    }
}
