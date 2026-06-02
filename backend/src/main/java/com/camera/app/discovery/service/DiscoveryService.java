package com.camera.app.discovery.service;

import com.camera.app.common.response.PageResult;
import com.camera.app.discovery.dto.*;
import com.camera.app.discovery.entity.DiscoveryLevel;
import com.camera.app.discovery.entity.DiscoverySourceType;

public interface DiscoveryService {

    DiscoveryTaskResponse createTask(DiscoveryTaskCreateRequest req);

    PageResult<DiscoveryTaskResponse> listTasks(int page, int size);

    DiscoveryTaskResponse getTask(Long taskId);

    /**
     * Stop a PENDING or RUNNING task immediately.
     * Sets status to CANCELED; already-discovered results are preserved.
     */
    DiscoveryTaskResponse stopTask(Long taskId);

    PageResult<DiscoveryResultResponse> listResults(Long taskId, Boolean managed,
                                                    DiscoverySourceType sourceType,
                                                    String deviceType, String keyword,
                                                    int page, int size);

    BatchImportResponse batchImport(BatchImportRequest req);

    DiscoveryTaskResponse importSingle(Long resultId, String defaultLocation, Long defaultOrgId);

    PageResult<DiscoveryResultResponse> listAllResults(Long taskId, Boolean managed,
                                                       DiscoverySourceType sourceType,
                                                       String deviceType,
                                                       DiscoveryLevel discoveryLevel,
                                                       String keyword, int page, int size);
}
