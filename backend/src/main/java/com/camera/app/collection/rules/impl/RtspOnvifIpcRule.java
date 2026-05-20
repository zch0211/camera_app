package com.camera.app.collection.rules.impl;

import com.camera.app.collection.rules.CategoryDetectionResult;
import com.camera.app.collection.rules.DeviceCategory;
import com.camera.app.collection.rules.DeviceTypeRule;
import com.camera.app.collection.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class RtspOnvifIpcRule implements DeviceTypeRule {

    @Override
    public String getName() { return "rtsp-onvif-ipc"; }

    @Override
    public DeviceCategory targetCategory() { return DeviceCategory.IPC; }

    @Override
    public BigDecimal evaluate(RuleContext ctx) {
        int score = 0;
        if (ctx.isRtspDetected()) score += 40;
        if (ctx.isOnvifDetected()) score += 40;
        if (ctx.getOpenPorts().contains(554)) score += 10;
        if (ctx.getAllTags().contains("camera-like")) score += 20;
        if (ctx.getAllTags().contains("nvr-like")) score -= 10;  // subtract if NVR hint
        score = Math.max(0, Math.min(100, score));
        return BigDecimal.valueOf(score / 100.0).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public String buildReason(RuleContext ctx) {
        List<String> facts = new ArrayList<>();
        if (ctx.isRtspDetected()) facts.add("RTSP 探测成功");
        if (ctx.isOnvifDetected()) facts.add("ONVIF 设备服务可达");
        if (ctx.getOpenPorts().contains(554)) facts.add("端口 554 开放");
        if (ctx.getAllTags().contains("camera-like")) facts.add("Web 指纹命中摄像头特征");
        return facts.isEmpty() ? "无充分依据" : "命中规则: " + String.join("; ", facts);
    }
}
