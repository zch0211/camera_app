package com.camera.app.collection.plugin;

import java.util.List;

public interface ProbePlugin {
    String getName();
    com.camera.app.collection.entity.ProbeType getProbeType();
    boolean supports(ProbeContext ctx);
    List<ProbeResult> execute(ProbeContext ctx);
}
