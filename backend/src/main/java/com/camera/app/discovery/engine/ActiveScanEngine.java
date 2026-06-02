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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Lightweight active discovery engine.
 *
 * Responsibility boundary (discovery layer only):
 *   - TCP connect probes to a small set of high-value ports
 *   - Banner grab (first 256 bytes) for lightweight vendor hint
 *   - Returns alive hosts + open ports + initial device type candidate
 *
 * NOT in scope here (handled by collection/profile layer):
 *   - RTSP / ONVIF / SNMP protocol deep probing
 *   - Service fingerprint building
 *   - Asset profile write-back
 *
 * Results are streamed via Consumer callback so callers can persist each
 * host immediately instead of waiting for the full scan to complete.
 */
@Slf4j
@Component
public class ActiveScanEngine {

    private static final int BANNER_READ_BYTES = 256;
    private static final int BANNER_SO_TIMEOUT_MS = 300;
    private static final int MAX_THREADS = 32;

    /**
     * Parallel-scan all IPs, calling {@code onResult} for EVERY completed IP
     * (alive or not). Callers should check {@link ScanHostResult#alive()}.
     *
     * The callback runs sequentially in the poll loop thread.
     * {@code stopSignal}: when set to {@code true}, the poll loop exits and
     * the scan pool is shut down; already-submitted port connects finish within
     * their timeout window.
     */
    public void scan(List<String> ips, List<Integer> ports, int timeoutMs,
                     AtomicBoolean stopSignal, Consumer<ScanHostResult> onResult) {
        if (ips.isEmpty()) return;

        int threads = Math.min(MAX_THREADS, ips.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("disc-scan-" + t.getId());
            return t;
        });
        CompletionService<ScanHostResult> ecs = new ExecutorCompletionService<>(pool);

        int submitted = 0;
        for (String ip : ips) {
            if (stopSignal.get()) break;
            final String finalIp = ip;
            ecs.submit(() -> scanOne(finalIp, ports, timeoutMs));
            submitted++;
        }
        // No more tasks; running ones will finish within their timeout
        pool.shutdown();

        // One poll timeout covers the worst case: all ports timed out for one IP
        long pollMs = Math.max((long) timeoutMs * ports.size() + 2000L, 5000L);

        for (int i = 0; i < submitted; i++) {
            if (stopSignal.get()) {
                pool.shutdownNow();
                break;
            }
            try {
                Future<ScanHostResult> f = ecs.poll(pollMs, TimeUnit.MILLISECONDS);
                if (f == null) {
                    log.debug("Poll timeout for one IP slot, continuing");
                    continue;
                }
                onResult.accept(f.get());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
                break;
            } catch (Exception e) {
                log.debug("Scan future error: {}", e.getMessage());
            }
        }

        if (!pool.isTerminated()) pool.shutdownNow();
    }

    /**
     * Scan a single IP: fast ICMP ping first, then TCP connect to each port.
     *
     * icmpAlive=true when InetAddress.isReachable() succeeds (uses TCP:7 on Windows
     * without admin privileges, real ICMP on Linux with root).
     * alive() = icmpAlive || any open TCP port — so ALIVE-only hosts (icmp but no ports)
     * are recorded at DiscoveryLevel.ALIVE; hosts with open ports become CANDIDATE.
     */
    ScanHostResult scanOne(String ip, List<Integer> ports, int timeoutMs) {
        boolean icmpAlive = false;
        try {
            icmpAlive = InetAddress.getByName(ip).isReachable(250);
        } catch (Exception ignored) {}

        List<Integer> openPorts = new ArrayList<>();
        String firstBanner = null;
        String vendorHint = null;

        for (int port : ports) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), timeoutMs);
                openPorts.add(port);
                if (firstBanner == null) {
                    firstBanner = tryBanner(socket);
                    if (firstBanner != null) vendorHint = extractVendorHint(firstBanner);
                }
            } catch (Exception ignored) {
                // port closed or filtered — normal
            }
        }

        return new ScanHostResult(ip, icmpAlive, openPorts, firstBanner, vendorHint);
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
        String lower = banner.toLowerCase();
        if (lower.contains("hikvision"))  return "Hikvision";
        if (lower.contains("dahua"))      return "Dahua";
        if (lower.contains("axis"))       return "Axis";
        if (lower.contains("reolink"))    return "Reolink";
        if (lower.contains("cisco"))      return "Cisco";
        if (lower.contains("huawei"))     return "Huawei";
        if (lower.contains("mikrotik"))   return "MikroTik";
        // Return first line as generic hint (capped at 64 chars)
        String firstLine = banner.split("[\r\n]")[0].trim();
        return firstLine.length() > 64 ? firstLine.substring(0, 64) : firstLine;
    }
}
