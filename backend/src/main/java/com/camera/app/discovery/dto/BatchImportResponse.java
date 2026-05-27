package com.camera.app.discovery.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class BatchImportResponse {

    private final int importedCount;
    private final int skippedCount;
    private final int failedCount;
    private final List<Long> importedAssetIds;
    private final List<String> failedIps;

    public BatchImportResponse(int importedCount, int skippedCount, int failedCount,
                               List<Long> importedAssetIds, List<String> failedIps) {
        this.importedCount = importedCount;
        this.skippedCount = skippedCount;
        this.failedCount = failedCount;
        this.importedAssetIds = importedAssetIds;
        this.failedIps = failedIps;
    }
}
