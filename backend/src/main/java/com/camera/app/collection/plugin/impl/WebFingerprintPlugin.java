package com.camera.app.collection.plugin.impl;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.plugin.ProbeContext;
import com.camera.app.collection.plugin.ProbePlugin;
import com.camera.app.collection.plugin.ProbeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Component
public class WebFingerprintPlugin implements ProbePlugin {

    private static final int READ_LIMIT = 16384;
    private static final List<String> PROBE_PATHS = List.of("/", "/login", "/login.html", "/web/index.html", "/doc/page/login.asp");

    private static final List<String> CAMERA_KW = List.of(
            "hikvision", "dahua", "axis", "reolink", "amcrest", "hanwha", "bosch",
            "vivotek", "uniview", "ip camera", "network camera", "surveillance", "ipc");
    private static final List<String> NVR_KW = List.of(
            "nvr", "network video recorder", "nvms", "ivms", "dsm", "video management");
    private static final List<String> ROUTER_KW = List.of(
            "router", "gateway", "firewall", "openwrt", "ddwrt", "mikrotik",
            "cisco", "huawei", "asus", "tp-link", "netgear", "dlink", "administration panel");

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final SSLSocketFactory TRUST_ALL_SSL;
    static {
        try {
            TrustManager[] tm = {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tm, new SecureRandom());
            TRUST_ALL_SSL = ctx.getSocketFactory();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @Override
    public String getName() { return "web-fingerprint"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.WEB_FINGERPRINT; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        List<ProbeResult> results = new ArrayList<>();
        LocalDateTime ts = LocalDateTime.now();

        for (int port : ctx.ports()) {
            for (String scheme : List.of("http", "https")) {
                if (scheme.equals("https") && port != 443 && port != 8443) continue;
                ProbeResult r = probeWebFingerprint(scheme, ctx.host(), port, ctx.timeoutMs(), ctx, ts);
                if (r != null) results.add(r);
            }
        }
        return results;
    }

    private ProbeResult probeWebFingerprint(String scheme, String host, int port,
                                              int timeoutMs, ProbeContext ctx, LocalDateTime ts) {
        Set<String> allTitles = new LinkedHashSet<>();
        Set<String> allKeywords = new LinkedHashSet<>();
        String serverHeader = null;
        boolean anySuccess = false;

        for (String path : PROBE_PATHS) {
            try {
                String url = scheme + "://" + host + ":" + port + path;
                HttpURLConnection conn = openConn(url, timeoutMs, scheme.equals("https"));
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                if (serverHeader == null) serverHeader = conn.getHeaderField("Server");
                int code = conn.getResponseCode();
                if (code >= 200 && code < 400) {
                    anySuccess = true;
                    String body = readBody(conn);
                    String title = extractTitle(body);
                    if (title != null && !title.isBlank()) allTitles.add(title.trim());
                    allKeywords.addAll(matchKeywords(body + " " + (title != null ? title : "")));
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }

        if (!anySuccess) return null;

        List<String> tags = new ArrayList<>();
        if (allKeywords.stream().anyMatch(k -> CAMERA_KW.stream().anyMatch(k::contains))) tags.add("camera-like");
        if (allKeywords.stream().anyMatch(k -> NVR_KW.stream().anyMatch(k::contains))) tags.add("nvr-like");
        if (allKeywords.stream().anyMatch(k -> ROUTER_KW.stream().anyMatch(k::contains))) tags.add("router-like");
        if (tags.isEmpty() && !allTitles.isEmpty()) tags.add("web-accessible");

        String titlesJoined = String.join(" | ", allTitles);
        String kwJoined = String.join(", ", allKeywords);
        String parsedJson = String.format(
                "{\"port\":%d,\"scheme\":\"%s\",\"titles\":%s,\"matchedKeywords\":%s,\"tags\":%s}",
                port, scheme,
                "\"" + titlesJoined.replace("\"", "'") + "\"",
                "\"" + kwJoined + "\"",
                tags.toString().replace("\"", "'"));

        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.WEB_FINGERPRINT)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(host).targetPort(port)
                .transportProtocol("TCP").applicationProtocol(scheme.toUpperCase())
                .success(true).portOpen(true)
                .confidence(new BigDecimal("0.900"))
                .rawData("Web fingerprint: titles=[" + titlesJoined + "] keywords=[" + kwJoined + "]")
                .parsedData(parsedJson)
                .webTitle(allTitles.isEmpty() ? null : allTitles.iterator().next())
                .serverHeader(serverHeader)
                .vendorHint(serverHeader)
                .tags(tags).collectedAt(ts)
                .build();
    }

    private HttpURLConnection openConn(String url, int timeout, boolean ssl) throws Exception {
        HttpURLConnection conn;
        if (ssl) {
            HttpsURLConnection https = (HttpsURLConnection) new URL(url).openConnection();
            https.setSSLSocketFactory(TRUST_ALL_SSL);
            https.setHostnameVerifier((h, s) -> true);
            conn = https;
        } else {
            conn = (HttpURLConnection) new URL(url).openConnection();
        }
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        return conn;
    }

    private String readBody(HttpURLConnection conn) {
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readNBytes(READ_LIMIT), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String extractTitle(String html) {
        if (html == null || html.isBlank()) return null;
        Matcher m = TITLE_PATTERN.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    private List<String> matchKeywords(String text) {
        String lower = text.toLowerCase();
        List<String> found = new ArrayList<>();
        for (String kw : CAMERA_KW) { if (lower.contains(kw)) found.add(kw); }
        for (String kw : NVR_KW)    { if (lower.contains(kw)) found.add(kw); }
        for (String kw : ROUTER_KW) { if (lower.contains(kw)) found.add(kw); }
        return found;
    }
}
