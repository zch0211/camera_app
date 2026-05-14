package com.camera.app.poc.service;

import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocExecutionLogResponse;
import com.camera.app.poc.dto.PocExecutionLogSummary;
import com.camera.app.poc.entity.PocExecutionLog;

public interface PocExecutionLogService {

    PocExecutionLog save(PocExecutionLog log);

    PageResult<PocExecutionLogSummary> list(Long pocId, Long assetId, Boolean success, int page, int size);

    PocExecutionLogResponse getById(Long id);
}
