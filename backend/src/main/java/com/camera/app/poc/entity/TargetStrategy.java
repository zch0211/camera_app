package com.camera.app.poc.entity;

public enum TargetStrategy {
    EXPLICIT_PORT,          // 用户显式指定端口
    RECOMMENDED_PORT_SCAN   // 自动扫描 POC 推荐端口列表
}
