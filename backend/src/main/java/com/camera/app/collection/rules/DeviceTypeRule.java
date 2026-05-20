package com.camera.app.collection.rules;

import com.camera.app.collection.plugin.ProbeResult;

import java.math.BigDecimal;
import java.util.List;

public interface DeviceTypeRule {
    String getName();
    DeviceCategory targetCategory();
    /** Returns 0.0 (no match) to 1.0 (certain match). */
    BigDecimal evaluate(RuleContext ctx);
    String buildReason(RuleContext ctx);
}
