package com.camera.app.dashboard;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardOverviewResponse {

    private long userCount;
    private long assetCount;
    private long onlineAssetCount;
    private long pocCount;
    private long enabledPocCount;
    private long recentExecutionCount;
    private boolean systemHealthy;
}
