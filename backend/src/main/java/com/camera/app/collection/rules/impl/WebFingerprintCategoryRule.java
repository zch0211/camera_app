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
public class WebFingerprintCategoryRule implements DeviceTypeRule {

    @Override
    public String getName() { return "web-fingerprint-category"; }

    @Override
    public DeviceCategory targetCategory() { return DeviceCategory.IPC; }

    @Override
    public BigDecimal evaluate(RuleContext ctx) {
        // This rule is web-fingerprint based, returns 0 if no web tags
        List<String> tags = ctx.getAllTags();
        if (tags.contains("camera-like") && !tags.contains("nvr-like") && !tags.contains("router-like")) {
            return new BigDecimal("0.600");
        }
        return BigDecimal.ZERO;
    }

    @Override
    public String buildReason(RuleContext ctx) {
        List<String> facts = new ArrayList<>();
        for (String t : ctx.getAllTags()) {
            if (t.contains("-like")) facts.add("Web 指纹标签: " + t);
        }
        for (String title : ctx.getWebTitles()) {
            facts.add("页面标题: " + title);
        }
        return facts.isEmpty() ? "无 Web 指纹依据" : String.join("; ", facts);
    }
}
