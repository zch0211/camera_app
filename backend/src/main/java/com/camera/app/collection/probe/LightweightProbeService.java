package com.camera.app.collection.probe;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LightweightProbeService {

    private static final int HTTP_READ_LIMIT = 8192;
    private static final int BANNER_READ_LIMIT = 512;
    private static final int BANNER_SO_TIMEOUT_MS = 300;

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final SSLSocketFactory  TRUST_ALL_SSL;
    private static final HostnameVerifier  TRUST_ALL_HV = (h, s) -> true;

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
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ---------- public result types ----------

    public record PortResult(int port, boolean open, String banner) {}

    public record HttpResult(int port, String protocol, String title,
                             String serverHeader, boolean success, String error) {}

    public record ProbeSession(String host,
                               List<PortResult> portResults,
                               List<HttpResult> httpResults,
                               LocalDateTime probedAt) {

        public List<Integer> openPorts() {
            return portResults.stream()
                    .filter(PortResult::open)
                    .map(PortResult::port)
                    .sorted()
                    .toList();
        }

        public List<String> detectedProtocols() {
            List<String> result = new ArrayList<>();
            for (HttpResult r : httpResults) {
                if (r.success() && r.protocol() != null && !result.contains(r.protocol())) {
                    result.add(r.protocol());
                }
            }
            return result;
        }

        public String bestWebTitle() {
            return httpResults.stream()
                    .filter(r -> r.success() && r.title() != null && !r.title().isBlank())
                    .map(HttpResult::title)
                    .findFirst().orElse(null);
        }

        public String bestVendorHint() {
            return httpResults.stream()
                    .filter(r -> r.success() && r.serverHeader() != null && !r.serverHeader().isBlank())
                    .map(HttpResult::serverHeader)
                    .findFirst().orElse(null);
        }
    }

    // ---------- main entry ----------

    public ProbeSession probe(String host, List<Integer> ports,
                              boolean enableHttp, boolean enableHttps, int timeoutMs) {
        LocalDateTime started = LocalDateTime.now();
        List<PortResult> portResults = new ArrayList<>();
        List<HttpResult> httpResults = new ArrayList<>();

        for (int port : ports) {
            PortResult pr = probePort(host, port, timeoutMs);
            portResults.add(pr);
            if (!pr.open()) continue;

            // 对所有已开放端口尝试 HTTP/HTTPS 探测，不限制端口白名单
            if (enableHttp)  httpResults.add(probeHttp(host, port, timeoutMs));
            if (enableHttps) httpResults.add(probeHttps(host, port, timeoutMs));
        }

        return new ProbeSession(host, portResults, httpResults, started);
    }

    // ---------- port probe ----------

    private PortResult probePort(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            String banner = tryReadBanner(socket);
            return new PortResult(port, true, banner);
        } catch (IOException e) {
            log.debug("Port {}:{} closed or unreachable: {}", host, port, e.getMessage());
            return new PortResult(port, false, null);
        }
    }

    private String tryReadBanner(Socket socket) {
        try {
            socket.setSoTimeout(BANNER_SO_TIMEOUT_MS);
            byte[] buf = new byte[BANNER_READ_LIMIT];
            int n = socket.getInputStream().read(buf);
            if (n > 0) return new String(buf, 0, n, StandardCharsets.UTF_8).trim();
        } catch (SocketTimeoutException ignored) {
        } catch (Exception e) {
            log.debug("Banner read failed: {}", e.getMessage());
        }
        return null;
    }

    // ---------- HTTP probe ----------

    private HttpResult probeHttp(String host, int port, int timeoutMs) {
        String url = "http://" + host + ":" + port + "/";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            String server = conn.getHeaderField("Server");
            String body   = readBody(conn);
            conn.disconnect();

            return new HttpResult(port, "HTTP", extractTitle(body), server, true, null);
        } catch (Exception e) {
            log.debug("HTTP probe {}:{} failed: {}", host, port, e.getMessage());
            return new HttpResult(port, "HTTP", null, null, false, e.getMessage());
        }
    }

    private HttpResult probeHttps(String host, int port, int timeoutMs) {
        String url = "https://" + host + ":" + port + "/";
        try {
            HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
            conn.setSSLSocketFactory(TRUST_ALL_SSL);
            conn.setHostnameVerifier(TRUST_ALL_HV);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            String server = conn.getHeaderField("Server");
            String body   = readBody(conn);
            conn.disconnect();

            return new HttpResult(port, "HTTPS", extractTitle(body), server, true, null);
        } catch (Exception e) {
            log.debug("HTTPS probe {}:{} failed: {}", host, port, e.getMessage());
            return new HttpResult(port, "HTTPS", null, null, false, e.getMessage());
        }
    }

    private String readBody(HttpURLConnection conn) {
        try (var is = conn.getInputStream()) {
            return new String(is.readNBytes(HTTP_READ_LIMIT), StandardCharsets.UTF_8);
        } catch (Exception e) {
            try (var es = conn.getErrorStream()) {
                if (es != null) return new String(es.readNBytes(HTTP_READ_LIMIT), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return "";
    }

    private String extractTitle(String html) {
        if (html == null || html.isBlank()) return null;
        Matcher m = TITLE_PATTERN.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }
}
