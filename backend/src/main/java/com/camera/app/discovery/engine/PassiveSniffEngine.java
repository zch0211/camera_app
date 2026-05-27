package com.camera.app.discovery.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passive sniff engine using pure-Java multicast listeners.
 * Listens on SSDP (UPnP), WS-Discovery, mDNS, and reads the ARP cache.
 * No raw packet capture required — works without root/admin on most platforms.
 * Results are deduplicated by IP and merged before returning.
 */
@Slf4j
@Component
public class PassiveSniffEngine {

    private static final String SSDP_ADDR    = "239.255.255.250";
    private static final int    SSDP_PORT    = 1900;
    private static final String WSDISCOVERY_ADDR = "239.255.255.250";
    private static final int    WSDISCOVERY_PORT = 3702;
    private static final String MDNS_ADDR    = "224.0.0.251";
    private static final int    MDNS_PORT    = 5353;

    private static final int UDP_BUF = 4096;

    // Patterns for parsing vendor from SSDP/mDNS strings
    private static final Pattern SERVER_PATTERN = Pattern.compile("(?i)server:\\s*(.+)");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?i)location:\\s*(https?://([\\d.]+)[:/])");
    private static final Pattern USN_PATTERN = Pattern.compile("(?i)usn:\\s*(.+)");
    private static final Pattern MAC_PATTERN = Pattern.compile("([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}");

    /**
     * Sniff for {@code durationSeconds} seconds on the given interface.
     * {@code networkInterface} may be null/blank to use default.
     */
    public List<SniffHostResult> sniff(String networkInterface, int durationSeconds) {
        Map<String, SniffEntry> discovered = new ConcurrentHashMap<>();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> tasks = new ArrayList<>();

        tasks.add(pool.submit(() -> listenSsdp(networkInterface, durationSeconds, discovered)));
        tasks.add(pool.submit(() -> listenWsDiscovery(networkInterface, durationSeconds, discovered)));
        tasks.add(pool.submit(() -> listenMdns(networkInterface, durationSeconds, discovered)));
        tasks.add(pool.submit(() -> readArpCache(discovered)));

        // Wait for duration; ARP cache read completes almost immediately
        try {
            pool.shutdown();
            pool.awaitTermination(durationSeconds + 5L, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }

        return discovered.values().stream().map(SniffEntry::toResult).toList();
    }

    // ---- SSDP listener ----

    private void listenSsdp(String iface, int durationSec, Map<String, SniffEntry> out) {
        try (MulticastSocket socket = buildMulticastSocket(SSDP_PORT, iface)) {
            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            socket.joinGroup(new InetSocketAddress(group, SSDP_PORT),
                    resolveInterface(iface));
            socket.setSoTimeout((durationSec * 1000));

            // Send M-SEARCH to provoke responses
            String msearch = "M-SEARCH * HTTP/1.1\r\nHOST: " + SSDP_ADDR + ":1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\nMX: 3\r\nST: ssdp:all\r\n\r\n";
            byte[] msearchBytes = msearch.getBytes(StandardCharsets.UTF_8);
            DatagramPacket probe = new DatagramPacket(msearchBytes, msearchBytes.length,
                    InetAddress.getByName(SSDP_ADDR), SSDP_PORT);
            socket.send(probe);

            byte[] buf = new byte[UDP_BUF];
            long deadline = System.currentTimeMillis() + durationSec * 1000L;
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(pkt);
                    String body = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                    String ip   = pkt.getAddress().getHostAddress();
                    parseSsdpPacket(ip, body, "SSDP", out);
                } catch (SocketTimeoutException ste) {
                    break;
                } catch (Exception e) {
                    log.debug("SSDP recv error: {}", e.getMessage());
                }
            }
            socket.leaveGroup(new InetSocketAddress(group, SSDP_PORT), resolveInterface(iface));
        } catch (Exception e) {
            log.info("SSDP listener not available: {}", e.getMessage());
        }
    }

    // ---- WS-Discovery listener ----

    private void listenWsDiscovery(String iface, int durationSec, Map<String, SniffEntry> out) {
        try (MulticastSocket socket = buildMulticastSocket(WSDISCOVERY_PORT, iface)) {
            InetAddress group = InetAddress.getByName(WSDISCOVERY_ADDR);
            socket.joinGroup(new InetSocketAddress(group, WSDISCOVERY_PORT),
                    resolveInterface(iface));
            socket.setSoTimeout(durationSec * 1000);

            // Send WS-Discovery Probe
            String probe =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\"" +
                " xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\"" +
                " xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\"" +
                " xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\">" +
                "<e:Header><w:MessageID>uuid:" + UUID.randomUUID() + "</w:MessageID>" +
                "<w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>" +
                "<w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>" +
                "<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>";

            byte[] probeBytes = probe.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(probeBytes, probeBytes.length,
                    InetAddress.getByName(WSDISCOVERY_ADDR), WSDISCOVERY_PORT));

            byte[] buf = new byte[UDP_BUF];
            long deadline = System.currentTimeMillis() + durationSec * 1000L;
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(pkt);
                    String body = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                    String ip   = pkt.getAddress().getHostAddress();
                    parseWsDiscoveryPacket(ip, body, out);
                } catch (SocketTimeoutException ste) {
                    break;
                } catch (Exception e) {
                    log.debug("WS-Disc recv error: {}", e.getMessage());
                }
            }
            socket.leaveGroup(new InetSocketAddress(group, WSDISCOVERY_PORT), resolveInterface(iface));
        } catch (Exception e) {
            log.info("WS-Discovery listener not available: {}", e.getMessage());
        }
    }

    // ---- mDNS listener ----

    private void listenMdns(String iface, int durationSec, Map<String, SniffEntry> out) {
        try (MulticastSocket socket = buildMulticastSocket(MDNS_PORT, iface)) {
            InetAddress group = InetAddress.getByName(MDNS_ADDR);
            socket.joinGroup(new InetSocketAddress(group, MDNS_PORT),
                    resolveInterface(iface));
            socket.setSoTimeout(durationSec * 1000);

            byte[] buf = new byte[UDP_BUF];
            long deadline = System.currentTimeMillis() + durationSec * 1000L;
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(pkt);
                    String ip = pkt.getAddress().getHostAddress();
                    // mDNS packets are binary DNS format; just record the sender IP as alive
                    out.computeIfAbsent(ip, k -> new SniffEntry(ip))
                       .addProtocol("mDNS")
                       .appendEvidence("mDNS packet from " + ip);
                } catch (SocketTimeoutException ste) {
                    break;
                } catch (Exception e) {
                    log.debug("mDNS recv error: {}", e.getMessage());
                }
            }
            socket.leaveGroup(new InetSocketAddress(group, MDNS_PORT), resolveInterface(iface));
        } catch (Exception e) {
            log.info("mDNS listener not available: {}", e.getMessage());
        }
    }

    // ---- ARP cache reader ----

    private void readArpCache(Map<String, SniffEntry> out) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            String cmd = os.contains("win") ? "arp -a" : "arp -n";
            Process proc = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseArpLine(line, out);
                }
            }
        } catch (Exception e) {
            log.info("ARP cache read failed: {}", e.getMessage());
        }
    }

    private static final Pattern ARP_IP_PATTERN = Pattern.compile(
            "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");

    private void parseArpLine(String line, Map<String, SniffEntry> out) {
        Matcher ipMatcher = ARP_IP_PATTERN.matcher(line);
        Matcher macMatcher = MAC_PATTERN.matcher(line);
        if (!ipMatcher.find()) return;
        String ip = ipMatcher.group(1);
        // Skip link-local and broadcast
        if (ip.startsWith("224.") || ip.startsWith("239.") || ip.endsWith(".255")) return;

        String mac = macMatcher.find() ? macMatcher.group(0) : null;
        String vendor = mac != null ? macVendorHint(mac) : null;

        SniffEntry entry = out.computeIfAbsent(ip, k -> new SniffEntry(ip));
        entry.addProtocol("ARP");
        if (mac != null) entry.setMac(mac);
        if (vendor != null) entry.setVendorHint(vendor);
        entry.appendEvidence("ARP: " + line.trim());
    }

    // ---- packet parsers ----

    private void parseSsdpPacket(String ip, String body, String proto, Map<String, SniffEntry> out) {
        SniffEntry entry = out.computeIfAbsent(ip, k -> new SniffEntry(ip));
        entry.addProtocol(proto);

        Matcher srv = SERVER_PATTERN.matcher(body);
        if (srv.find()) {
            String server = srv.group(1).trim();
            entry.setVendorHint(extractVendor(server));
        }

        Matcher usn = USN_PATTERN.matcher(body);
        if (usn.find()) entry.appendEvidence("USN: " + usn.group(1).trim());

        entry.appendEvidence(proto + " from " + ip);
    }

    private void parseWsDiscoveryPacket(String ip, String body, Map<String, SniffEntry> out) {
        SniffEntry entry = out.computeIfAbsent(ip, k -> new SniffEntry(ip));
        entry.addProtocol("WS-Discovery");
        if (body.contains("NetworkVideoTransmitter") || body.contains("onvif")) {
            entry.addProtocol("ONVIF");
            if (entry.getVendorHint() == null) entry.setVendorHint("ONVIF device");
        }
        // Extract XAddr (endpoint URL) if present
        int xAddrStart = body.indexOf("<d:XAddrs>");
        int xAddrEnd   = body.indexOf("</d:XAddrs>");
        if (xAddrStart >= 0 && xAddrEnd > xAddrStart) {
            String xaddr = body.substring(xAddrStart + 10, xAddrEnd).trim();
            entry.appendEvidence("XAddr: " + (xaddr.length() > 128 ? xaddr.substring(0, 128) : xaddr));
        }
        entry.appendEvidence("WS-Discovery from " + ip);
    }

    // ---- helpers ----

    private MulticastSocket buildMulticastSocket(int port, String iface) throws Exception {
        MulticastSocket socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        NetworkInterface ni = resolveInterface(iface);
        if (ni != null) socket.setNetworkInterface(ni);
        socket.setTimeToLive(4);
        return socket;
    }

    private NetworkInterface resolveInterface(String iface) {
        if (iface == null || iface.isBlank()) return null;
        try { return NetworkInterface.getByName(iface); } catch (Exception e) { return null; }
    }

    private String extractVendor(String server) {
        if (server == null) return null;
        String lower = server.toLowerCase();
        if (lower.contains("hikvision"))  return "Hikvision";
        if (lower.contains("dahua"))      return "Dahua";
        if (lower.contains("axis"))       return "Axis";
        if (lower.contains("reolink"))    return "Reolink";
        if (lower.contains("cisco"))      return "Cisco";
        if (lower.contains("huawei"))     return "Huawei";
        if (lower.contains("mikrotik"))   return "MikroTik";
        return server.length() > 64 ? server.substring(0, 64) : server;
    }

    private String macVendorHint(String mac) {
        // OUI-based vendor hints for common surveillance camera manufacturers
        String oui = mac.replace("-", ":").toUpperCase().substring(0, 8);
        return switch (oui) {
            case "D4:E8:53", "80:DC:A9", "18:68:CB" -> "Hikvision";
            case "90:02:A9", "BC:25:E0", "D8:E2:DF" -> "Dahua";
            case "00:40:8C", "AC:CC:8E" -> "Axis";
            case "B0:C5:CA" -> "Reolink";
            default -> null;
        };
    }

    // ---- inner accumulator ----

    static class SniffEntry {
        private final String ip;
        private String mac;
        private String hostname;
        private String vendorHint;
        private final List<String> protocols = new ArrayList<>();
        private final StringBuilder evidence = new StringBuilder();

        SniffEntry(String ip) { this.ip = ip; }

        SniffEntry addProtocol(String p) {
            if (!protocols.contains(p)) protocols.add(p);
            return this;
        }

        void setMac(String mac) { if (this.mac == null) this.mac = mac; }
        void setVendorHint(String v) { if (this.vendorHint == null) this.vendorHint = v; }
        void setHostname(String h) { if (this.hostname == null) this.hostname = h; }
        String getVendorHint() { return vendorHint; }

        SniffEntry appendEvidence(String s) {
            if (evidence.length() > 0) evidence.append("; ");
            if (evidence.length() + s.length() < 1024) evidence.append(s);
            return this;
        }

        SniffHostResult toResult() {
            return new SniffHostResult(ip, mac, hostname, vendorHint,
                    List.copyOf(protocols), evidence.toString());
        }
    }
}
