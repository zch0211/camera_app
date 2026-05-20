package com.camera.app.collection.entity;

public enum TaskPreset {
    /** 摄像头预设：端口探测 + HTTP/HTTPS + RTSP + ONVIF + Web 指纹 */
    CAMERA_PRESET,
    /** NVR 预设：摄像头全部 + SSH Banner + Telnet Banner */
    NVR_PRESET,
    /** 路由器预设：端口探测 + HTTP/HTTPS + SNMP + SSH Banner + Telnet Banner + Web 指纹 */
    ROUTER_PRESET,
    /** 全量预设：尽可能全开，保持无攻击性 */
    FULL_PRESET,
    /** 自定义：由 enabledPlugins 指定 */
    CUSTOM
}
