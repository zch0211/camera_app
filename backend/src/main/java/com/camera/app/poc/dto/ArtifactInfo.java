package com.camera.app.poc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@Schema(description = "执行结果附件元数据（图片 / 文件）")
public class ArtifactInfo {

    @Schema(description = "文件名", example = "snapshot.jpg")
    private String name;

    @Schema(description = "类型：IMAGE / FILE")
    private String type;

    @Schema(description = "MIME 类型", example = "image/jpeg")
    private String mimeType;

    @Schema(description = "文件大小（字节）")
    private Long sizeBytes;

    @Schema(description = "MinIO 对象键（后端存储路径，用于下载）")
    private String objectKey;

    @Schema(description = "存储路径（与 objectKey 相同，保留兼容）")
    private String path;

    @Schema(description = "下载链接（相对 URL，格式：/api/v1/poc-executions/{id}/artifacts/{name}/download）")
    private String downloadUrl;

    @Schema(description = "在线预览链接（IMAGE 类型时与 downloadUrl 相同，FILE 类型时为 null）")
    private String previewUrl;

    @Schema(description = "是否可在线预览（IMAGE 类型为 true）")
    private boolean previewable;

    @Schema(description = "是否可下载")
    private boolean downloadable;
}
