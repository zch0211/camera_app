package com.camera.app.poc.entity;

public enum ActionCategory {
    VERIFY,    // 检测/验证类（只读，低风险）
    READ,      // 读取类（只读，低风险）
    DOWNLOAD,  // 下载类（只读，低风险）
    INTERACT   // 交互/执行类（高风险，可能改变系统状态）
}
