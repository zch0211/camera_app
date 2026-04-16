package com.camera.app.poc.service;

import java.io.InputStream;

/**
 * 文件下载结果，由 Controller 用来构造 ResponseEntity
 */
public record PocDownloadResult(
        String originalFilename,
        String contentType,
        Long fileSize,
        InputStream inputStream
) {}
