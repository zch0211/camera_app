package com.camera.app.collection.service;

import com.camera.app.collection.dto.CollectionResultResponse;
import com.camera.app.collection.dto.CollectionTaskCreateRequest;
import com.camera.app.collection.dto.CollectionTaskResponse;
import com.camera.app.common.response.PageResult;

import java.util.List;

public interface AssetCollectionService {

    CollectionTaskResponse createAndExecuteTask(Long assetId, CollectionTaskCreateRequest request);

    PageResult<CollectionTaskResponse> listTasks(Long assetId, int page, int size);

    CollectionTaskResponse getTask(Long assetId, Long taskId);

    List<CollectionResultResponse> getTaskResults(Long assetId, Long taskId);
}
