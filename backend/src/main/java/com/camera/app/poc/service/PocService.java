package com.camera.app.poc.service;

import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocListItemResponse;
import com.camera.app.poc.dto.PocResponse;
import com.camera.app.poc.dto.PocUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

public interface PocService {

    PageResult<PocListItemResponse> listPocs(
            String keyword, String severity, Boolean enabled,
            String language, String targetType,
            int page, int size);

    PocResponse getPoc(Long id);

    PocResponse uploadPoc(
            MultipartFile file,
            String name,
            String description,
            String cveId,
            String severity,
            String language,
            String targetType,
            String vendor,
            String protocol,
            String entryPoint,
            boolean enabled,
            String createdBy);

    PocDownloadResult downloadPoc(Long id);

    PocResponse updatePoc(Long id, PocUpdateRequest request);

    void deletePoc(Long id);
}
