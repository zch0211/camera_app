package com.camera.app.collection.plugin.impl;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.plugin.ProbeContext;
import com.camera.app.collection.plugin.ProbePlugin;
import com.camera.app.collection.plugin.ProbeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.net.ssl.*;
import javax.xml.parsers.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class OnvifProbePlugin implements ProbePlugin {

    private static final String DEVICE_SERVICE_PATH = "/onvif/device_service";
    private static final List<Integer> ONVIF_PORTS = List.of(80, 8080, 8000, 8899, 8888);
    private static final String GET_DEVICE_INFO_SOAP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
            "xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">" +
            "<s:Body><tds:GetDeviceInformation/></s:Body></s:Envelope>";

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
    public String getName() { return "onvif-probe"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.ONVIF_PROBE; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        List<ProbeResult> results = new ArrayList<>();
        LocalDateTime ts = LocalDateTime.now();

        Set<Integer> portsToTry = new LinkedHashSet<>(ONVIF_PORTS);
        ctx.ports().stream().filter(p -> p == 80 || p == 8080 || p == 8000 || p == 8888 || p == 8899).forEach(portsToTry::add);

        for (int port : portsToTry) {
            ProbeResult r = probeOnvif(ctx.host(), port, ctx.timeoutMs(), ctx, ts);
            results.add(r);
            if (r.isSuccess()) break;  // found ONVIF, no need to try more ports
        }
        return results;
    }

    private ProbeResult probeOnvif(String host, int port, int timeoutMs,
                                    ProbeContext ctx, LocalDateTime ts) {
        String url = "http://" + host + ":" + port + DEVICE_SERVICE_PATH;
        try {
            HttpURLConnection conn = openConnection(url, timeoutMs);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "CameraScanner/1.0");
            conn.setDoOutput(true);
            conn.connect();

            try (OutputStream os = conn.getOutputStream()) {
                os.write(GET_DEVICE_INFO_SOAP.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = conn.getResponseCode();

            if (statusCode == 401 || statusCode == 403) {
                conn.disconnect();
                return ProbeResult.builder()
                        .pluginName(getName()).probeType(ProbeType.ONVIF_PROBE)
                        .assetId(ctx.assetId()).taskId(ctx.taskId())
                        .targetHost(host).targetPort(port)
                        .transportProtocol("TCP").applicationProtocol("ONVIF")
                        .success(true).portOpen(true)
                        .confidence(new BigDecimal("0.800"))
                        .rawData("ONVIF endpoint detected, authentication required (HTTP " + statusCode + ")")
                        .parsedData(String.format("{\"port\":%d,\"status\":%d,\"authRequired\":true}", port, statusCode))
                        .tags(List.of("onvif-detected", "camera-like"))
                        .collectedAt(ts).build();
            }

            if (statusCode != 200) {
                conn.disconnect();
                return failResult(ctx, port, ts, "HTTP " + statusCode);
            }

            String body = readStream(conn.getInputStream());
            conn.disconnect();

            Map<String, String> info = parseDeviceInfo(body);
            List<String> tags = new ArrayList<>(List.of("onvif-detected", "camera-like"));

            String parsedJson = String.format(
                    "{\"port\":%d,\"manufacturer\":%s,\"model\":%s,\"firmware\":%s,\"serial\":%s}",
                    port,
                    jsonStr(info.get("Manufacturer")),
                    jsonStr(info.get("Model")),
                    jsonStr(info.get("FirmwareVersion")),
                    jsonStr(info.get("SerialNumber")));

            return ProbeResult.builder()
                    .pluginName(getName()).probeType(ProbeType.ONVIF_PROBE)
                    .assetId(ctx.assetId()).taskId(ctx.taskId())
                    .targetHost(host).targetPort(port)
                    .transportProtocol("TCP").applicationProtocol("ONVIF")
                    .success(true).portOpen(true)
                    .confidence(new BigDecimal("0.980"))
                    .rawData("ONVIF GetDeviceInformation succeeded: " + info)
                    .parsedData(parsedJson)
                    .manufacturer(info.get("Manufacturer"))
                    .model(info.get("Model"))
                    .firmwareVersion(info.get("FirmwareVersion"))
                    .serialNumber(info.get("SerialNumber"))
                    .vendorHint(info.get("Manufacturer"))
                    .tags(tags).collectedAt(ts)
                    .build();

        } catch (Exception e) {
            log.debug("ONVIF probe {}:{} failed: {}", host, port, e.getMessage());
            return failResult(ctx, port, ts, e.getMessage());
        }
    }

    private HttpURLConnection openConnection(String url, int timeoutMs) throws Exception {
        HttpURLConnection conn;
        if (url.startsWith("https")) {
            HttpsURLConnection https = (HttpsURLConnection) new URL(url).openConnection();
            https.setSSLSocketFactory(TRUST_ALL_SSL);
            https.setHostnameVerifier((h, s) -> true);
            conn = https;
        } else {
            conn = (HttpURLConnection) new URL(url).openConnection();
        }
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private Map<String, String> parseDeviceInfo(String xml) {
        Map<String, String> info = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            for (String field : List.of("Manufacturer", "Model", "FirmwareVersion", "SerialNumber", "HardwareId")) {
                NodeList nl = doc.getElementsByTagNameNS("*", field);
                if (nl.getLength() > 0) {
                    info.put(field, nl.item(0).getTextContent().trim());
                }
            }
        } catch (Exception e) {
            log.debug("ONVIF XML parse error: {}", e.getMessage());
        }
        return info;
    }

    private String readStream(InputStream is) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String jsonStr(String s) { return s != null ? "\"" + s.replace("\"", "'") + "\"" : "null"; }

    private ProbeResult failResult(ProbeContext ctx, int port, LocalDateTime ts, String error) {
        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.ONVIF_PROBE)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(ctx.host()).targetPort(port)
                .transportProtocol("TCP").applicationProtocol("ONVIF")
                .success(false).portOpen(false)
                .confidence(BigDecimal.ZERO)
                .errorMessage(error).collectedAt(ts).build();
    }
}
