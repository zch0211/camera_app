package com.camera.app.discovery.engine;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses targetScope strings into concrete IP address lists.
 * Supports: single IP, dash range (x.x.x.a-x.x.x.b), CIDR (x.x.x.x/n).
 * Hard cap of 1024 IPs to prevent accidental large scans.
 */
public final class IpRangeExpander {

    private static final int MAX_IPS = 1024;

    private IpRangeExpander() {}

    public static List<String> expand(String scope) {
        if (scope == null || scope.isBlank()) return Collections.emptyList();
        scope = scope.trim();
        if (scope.contains("/")) return expandCidr(scope);
        if (scope.contains("-")) return expandRange(scope);
        return List.of(scope);
    }

    private static List<String> expandCidr(String cidr) {
        try {
            String[] parts = cidr.split("/");
            int prefix = Integer.parseInt(parts[1].trim());
            if (prefix < 8 || prefix > 32) return Collections.emptyList();

            long baseIp = ipToLong(parts[0].trim());
            long mask   = prefix == 32 ? 0xFFFFFFFFL : ((0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL);
            long network = baseIp & mask;
            long host   = 0xFFFFFFFFL & ~mask;

            List<String> ips = new ArrayList<>();
            // Skip network address (.0) and broadcast (.255) for prefix < 31
            long start = (prefix < 31) ? network + 1 : network;
            long end   = (prefix < 31) ? network + host - 1 : network + host;

            for (long i = start; i <= end && ips.size() < MAX_IPS; i++) {
                ips.add(longToIp(i));
            }
            return ips;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<String> expandRange(String range) {
        String[] parts = range.split("-");
        if (parts.length != 2) return Collections.emptyList();
        try {
            long start = ipToLong(parts[0].trim());
            long end   = ipToLong(parts[1].trim());
            if (end < start) return Collections.emptyList();

            List<String> ips = new ArrayList<>();
            for (long i = start; i <= end && ips.size() < MAX_IPS; i++) {
                ips.add(longToIp(i));
            }
            return ips;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static long ipToLong(String ip) throws UnknownHostException {
        byte[] addr = InetAddress.getByName(ip).getAddress();
        long val = 0;
        for (byte b : addr) val = (val << 8) | (b & 0xFF);
        return val;
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." +
               ((ip >> 8)  & 0xFF) + "." +  (ip        & 0xFF);
    }
}
