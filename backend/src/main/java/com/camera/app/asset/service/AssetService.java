package com.camera.app.asset.service;

import com.camera.app.asset.dto.AssetCreateRequest;
import com.camera.app.asset.dto.AssetResponse;
import com.camera.app.asset.dto.AssetUpdateRequest;
import com.camera.app.common.response.PageResult;

public interface AssetService {

    PageResult<AssetResponse> listAssets(String keyword, String brand, String model,
                                         Boolean online, int page, int size);

    AssetResponse getAsset(Long id);

    AssetResponse createAsset(AssetCreateRequest request);

    AssetResponse updateAsset(Long id, AssetUpdateRequest request);

    void deleteAsset(Long id);
}
