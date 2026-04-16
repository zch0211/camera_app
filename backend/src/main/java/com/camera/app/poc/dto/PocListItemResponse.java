package com.camera.app.poc.dto;

import com.camera.app.poc.entity.Poc;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * POC 列表条目响应（精简字段，不含大文本描述和文件校验和）
 */
@Getter
@Schema(description = "POC 列表条目")
public class PocListItemResponse {

    @Schema(description = "POC ID")
    private final Long id;

    @Schema(description = "名称")
    private final String name;

    @Schema(description = "CVE 编号")
    private final String cveId;

    @Schema(description = "严重等级")
    private final String severity;

    @Schema(description = "文件语言")
    private final String language;

    @Schema(description = "目标类型")
    private final String targetType;

    @Schema(description = "适用厂商")
    private final String vendor;

    @Schema(description = "协议类型")
    private final String protocol;

    @Schema(description = "原始文件名")
    private final String originalFilename;

    @Schema(description = "文件大小（字节）")
    private final Long fileSize;

    @Schema(description = "是否启用")
    private final boolean enabled;

    @Schema(description = "状态：ACTIVE / DISABLED / DELETED")
    private final String status;

    @Schema(description = "上传者")
    private final String createdBy;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    public PocListItemResponse(Poc poc) {
        this.id = poc.getId();
        this.name = poc.getName();
        this.cveId = poc.getCveId();
        this.severity = poc.getSeverity() != null ? poc.getSeverity().name() : null;
        this.language = poc.getLanguage() != null ? poc.getLanguage().name() : null;
        this.targetType = poc.getTargetType() != null ? poc.getTargetType().name() : null;
        this.vendor = poc.getVendor();
        this.protocol = poc.getProtocol() != null ? poc.getProtocol().name() : null;
        this.originalFilename = poc.getOriginalFilename();
        this.fileSize = poc.getFileSize();
        this.enabled = poc.isEnabled();
        this.status = poc.getStatus() != null ? poc.getStatus().name() : null;
        this.createdBy = poc.getCreatedBy();
        this.createdAt = poc.getCreatedAt();
    }
}
