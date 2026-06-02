package com.camera.app.poc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
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

    @Schema(description = "存储路径或 MinIO object key")
    private String path;

    @Schema(description = "是否可在线预览")
    private boolean previewable;

    @Schema(description = "是否可下载")
    private boolean downloadable;
}
