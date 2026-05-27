package com.camera.app.discovery.service;

import com.camera.app.discovery.dto.*;
import com.camera.app.discovery.entity.DiscoverySourceType;
import com.camera.app.common.response.PageResult;

public interface DiscoveryService {

    DiscoveryTaskResponse createTask(DiscoveryTaskCreateRequest req);

    PageResult<DiscoveryTaskResponse> listTasks(int page, int size);

    DiscoveryTaskResponse getTask(Long taskId);

    PageResult<DiscoveryResultResponse> listResults(Long taskId, Boolean managed,
                                                    DiscoverySourceType sourceType,
                                                    String deviceType, String keyword,
                                                    int page, int size);

    BatchImportResponse batchImport(BatchImportRequest req);

    DiscoveryTaskResponse importSingle(Long resultId, String defaultLocation, Long defaultOrgId);
}
