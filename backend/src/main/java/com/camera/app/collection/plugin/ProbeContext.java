package com.camera.app.collection.plugin;

import com.camera.app.collection.entity.TaskPreset;

import java.util.List;
import java.util.Map;

public record ProbeContext(
        Long assetId,
        Long taskId,
        String host,
        List<Integer> ports,
        int timeoutMs,
        TaskPreset preset,
        Map<String, Object> config
) {}
