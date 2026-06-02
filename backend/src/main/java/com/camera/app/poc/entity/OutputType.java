package com.camera.app.poc.entity;

public enum OutputType {
    BOOLEAN_TEXT, // 布尔结果 + 文本摘要（用于漏洞检测类动作）
    TEXT,         // 纯文本（用于命令执行输出）
    JSON,         // 结构化 JSON（用于列表类读取）
    IMAGE,        // 图片（artifact，需前端特殊渲染）
    FILE,         // 文件（artifact，提供下载）
    MIXED         // 混合（文本 + artifact）
}
