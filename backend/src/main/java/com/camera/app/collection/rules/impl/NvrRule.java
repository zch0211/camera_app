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
public class NvrRule implements DeviceTypeRule {

    @Override
    public String getName() { return "nvr-multi-channel"; }

    @Override
    public DeviceCategory targetCategory() { return DeviceCategory.NVR; }

    @Override
    public BigDecimal evaluate(RuleContext ctx) {
        int score = 0;
        if (ctx.isRtspDetected()) score += 30;
        if (ctx.isOnvifDetected()) score += 20;
        if (ctx.getAllTags().contains("nvr-like")) score += 40;
        // NVR typically has both SSH and RTSP
        if (ctx.getSshBanner() != null && ctx.isRtspDetected()) score += 15;
        // Dahua NVR port
        if (ctx.getOpenPorts().contains(37777) || ctx.getOpenPorts().contains(34568)) score += 20;
        if (ctx.getAllTags().contains("camera-like") && !ctx.getAllTags().contains("nvr-like")) score -= 15;
        score = Math.max(0, Math.min(100, score));
        return BigDecimal.valueOf(score / 100.0).setScale(3, RoundingMode.HALF_UP);
    }

    @Override
    public String buildReason(RuleContext ctx) {
        List<String> facts = new ArrayList<>();
        if (ctx.getAllTags().contains("nvr-like")) facts.add("Web 指纹命中 NVR 特征");
        if (ctx.isRtspDetected()) facts.add("RTSP 探测成功");
        if (ctx.isOnvifDetected()) facts.add("ONVIF 可达");
        if (ctx.getOpenPorts().contains(37777)) facts.add("Dahua 管理端口 37777 开放");
        if (ctx.getOpenPorts().contains(34568)) facts.add("Dahua 管理端口 34568 开放");
        if (ctx.getSshBanner() != null) facts.add("SSH Banner 可读");
        return facts.isEmpty() ? "无充分依据" : "命中规则: " + String.join("; ", facts);
    }
}
