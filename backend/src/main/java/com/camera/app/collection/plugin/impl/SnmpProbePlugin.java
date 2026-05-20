package com.camera.app.collection.plugin.impl;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.plugin.ProbeContext;
import com.camera.app.collection.plugin.ProbePlugin;
import com.camera.app.collection.plugin.ProbeResult;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class SnmpProbePlugin implements ProbePlugin {

    private static final int SNMP_PORT = 161;
    private static final List<String> DEFAULT_COMMUNITIES = List.of("public");
    private static final String OID_SYS_DESCR  = "1.3.6.1.2.1.1.1.0";
    private static final String OID_SYS_NAME   = "1.3.6.1.2.1.1.5.0";
    private static final String OID_SYS_OBJ_ID = "1.3.6.1.2.1.1.2.0";

    @Override
    public String getName() { return "snmp-probe"; }

    @Override
    public ProbeType getProbeType() { return ProbeType.SNMP_PROBE; }

    @Override
    public boolean supports(ProbeContext ctx) { return true; }

    @SuppressWarnings("unchecked")
    private List<String> communities(ProbeContext ctx) {
        Object cfg = ctx.config() != null ? ctx.config().get("snmp.communities") : null;
        if (cfg instanceof List) return (List<String>) cfg;
        return DEFAULT_COMMUNITIES;
    }

    @Override
    public List<ProbeResult> execute(ProbeContext ctx) {
        LocalDateTime ts = LocalDateTime.now();
        List<String> communities = communities(ctx);

        for (String community : communities) {
            ProbeResult result = snmpGet(ctx.host(), SNMP_PORT, community, ctx.timeoutMs(), ctx, ts);
            if (result.isSuccess()) return List.of(result);
        }
        return List.of(failResult(ctx, ts, "No response from any community"));
    }

    private ProbeResult snmpGet(String host, int port, String community,
                                 int timeoutMs, ProbeContext ctx, LocalDateTime ts) {
        Snmp snmp = null;
        try {
            DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();

            CommunityTarget<UdpAddress> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(community));
            target.setAddress(new UdpAddress(java.net.InetAddress.getByName(host), port));
            target.setRetries(1);
            target.setTimeout(timeoutMs);
            target.setVersion(SnmpConstants.version2c);

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(OID_SYS_DESCR)));
            pdu.add(new VariableBinding(new OID(OID_SYS_NAME)));
            pdu.add(new VariableBinding(new OID(OID_SYS_OBJ_ID)));
            pdu.setType(PDU.GET);

            ResponseEvent<?> event = snmp.get(pdu, target);
            if (event == null || event.getResponse() == null) {
                return failResult(ctx, ts, "No SNMP response");
            }

            PDU response = event.getResponse();
            String sysDescr = null, sysName = null, sysObjId = null;
            for (VariableBinding vb : response.getVariableBindings()) {
                String oid = vb.getOid().toString();
                String val = vb.getVariable().toString();
                if (oid.startsWith("1.3.6.1.2.1.1.1")) sysDescr = val;
                else if (oid.startsWith("1.3.6.1.2.1.1.5")) sysName = val;
                else if (oid.startsWith("1.3.6.1.2.1.1.2")) sysObjId = val;
            }

            String rawSummary = "sysName=" + sysName + " sysDescr=" + sysDescr + " sysObjectID=" + sysObjId;
            String parsedJson = String.format(
                    "{\"port\":%d,\"community\":\"%s\",\"sysName\":%s,\"sysDescr\":%s,\"sysObjectID\":%s}",
                    port, community,
                    sysName != null ? "\"" + sysName + "\"" : "null",
                    sysDescr != null ? "\"" + sysDescr.replace("\"", "'") + "\"" : "null",
                    sysObjId != null ? "\"" + sysObjId + "\"" : "null");

            return ProbeResult.builder()
                    .pluginName(getName()).probeType(ProbeType.SNMP_PROBE)
                    .assetId(ctx.assetId()).taskId(ctx.taskId())
                    .targetHost(host).targetPort(port)
                    .transportProtocol("UDP").applicationProtocol("SNMP")
                    .success(true).portOpen(true)
                    .confidence(new BigDecimal("0.950"))
                    .rawData(rawSummary).parsedData(parsedJson)
                    .vendorHint(sysName)
                    .serviceBanner(sysDescr)
                    .collectedAt(ts).build();

        } catch (Exception e) {
            log.debug("SNMP probe {}:{} failed: {}", host, port, e.getMessage());
            return failResult(ctx, ts, e.getMessage());
        } finally {
            if (snmp != null) { try { snmp.close(); } catch (Exception ignored) {} }
        }
    }

    private ProbeResult failResult(ProbeContext ctx, LocalDateTime ts, String error) {
        return ProbeResult.builder()
                .pluginName(getName()).probeType(ProbeType.SNMP_PROBE)
                .assetId(ctx.assetId()).taskId(ctx.taskId())
                .targetHost(ctx.host()).targetPort(SNMP_PORT)
                .transportProtocol("UDP").applicationProtocol("SNMP")
                .success(false).portOpen(false)
                .confidence(BigDecimal.ZERO).errorMessage(error).collectedAt(ts).build();
    }
}
