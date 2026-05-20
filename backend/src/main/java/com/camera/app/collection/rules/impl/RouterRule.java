package com.camera.app.collection.rules.impl;

import com.camera.app.collection.rules.DeviceCategory;
import com.camera.app.collection.rules.DeviceTypeRule;
import com.camera.app.collection.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class RouterRule implements DeviceTypeRule {

    @Override
    public String getName() { return "router-ssh-snmp"; }

    @Override
    public DeviceCategory targetCategory() { return DeviceCategory.ROUTER; }

    @Override
    public BigDecimal evaluate(RuleContext ctx) {
        int score = 0;
        if (ctx.getSnmpSysDescr() != null) score += 40;
        if (ctx.getSshBanner() != null) score += 20;
        if (ctx.getTelnetBanner() != null) score += 15;
        if (ctx.getAllTags().contains("router-like")) score += 30;
        // Routers don't have RTSP/ONVIF
        if (ctx.isRtspDetected()) score -= 30;
        if (ctx.isOnvifDetected()) score -= 20;
        // SNMP sysDescr keywords
        if (ctx.getSnmpSysDescr() != null) {
            String d = ctx.getSnmpSysDescr().toLowerCase();
            if (d.contains("router") || d.contains("cisco") || d.contains("huawei")
                    || d.contains("juniper") || d.contains("gateway")) score += 20;
        }
        score = Math.max(0, Math.min(100, score));
        return BigDecimal.valueOf(score / 100.0).setScale(3, RoundingMode.HALF_UP);
    }

    @Override
    public String buildReason(RuleContext ctx) {
        List<String> facts = new ArrayList<>();
        if (ctx.getSnmpSysDescr() != null) facts.add("SNMP sysDescr: " + ctx.getSnmpSysDescr());
        if (ctx.getSshBanner() != null) facts.add("SSH Banner 可读");
        if (ctx.getTelnetBanner() != null) facts.add("Telnet Banner 可读");
        if (ctx.getAllTags().contains("router-like")) facts.add("Web 指纹命中路由器特征");
        return facts.isEmpty() ? "无充分依据" : "命中规则: " + String.join("; ", facts);
    }
}
