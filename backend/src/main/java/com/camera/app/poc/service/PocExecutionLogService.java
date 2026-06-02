package com.camera.app.poc.service;

import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocExecutionLogResponse;
import com.camera.app.poc.dto.PocExecutionLogSummary;
import com.camera.app.poc.entity.PocExecutionLog;

public interface PocExecutionLogService {

    PocExecutionLog save(PocExecutionLog log);

    PageResult<PocExecutionLogSummary> list(Long pocId, Long assetId, Boolean success, int page, int size);

    PocExecutionLogResponse getById(Long id);

    /**
     * 下载指定执行记录中的附件文件（IMAGE / FILE 类动作产生）。
     *
     * @param logId 执行记录 ID
     * @param name  附件文件名，与 artifactSummary 中的 name 字段一致
     * @return 文件下载结果，调用方负责关闭流
     */
    PocDownloadResult downloadArtifact(Long logId, String name);
}
