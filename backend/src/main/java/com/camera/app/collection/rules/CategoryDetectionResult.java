package com.camera.app.collection.rules;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CategoryDetectionResult {
    private final DeviceCategory category;
    private final BigDecimal confidence;
    private final String reason;
    private final List<String> supportingFacts;
}
