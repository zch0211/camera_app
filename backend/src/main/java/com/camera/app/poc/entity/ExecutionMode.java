package com.camera.app.poc.entity;

public enum ExecutionMode {
    CHECK,   // 安全检测/验证，只读型，不改变目标状态
    EXPLOIT  // 漏洞利用，高风险，可能改变目标状态
}
