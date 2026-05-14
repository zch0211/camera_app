package com.camera.app.poc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "执行模式参数字段定义，用于前端动态渲染参数表单")
public class ParamField {

    @Schema(description = "参数名，对应 params.<name>", example = "cmd")
    private String name;

    @Schema(description = "前端显示标签", example = "命令")
    private String label;

    @Schema(description = "字段类型：text / number / select", example = "text")
    private String type;

    @Schema(description = "是否必填")
    private boolean required;

    @Schema(description = "输入框占位提示", example = "请输入要执行的命令，如 whoami")
    private String placeholder;
}
