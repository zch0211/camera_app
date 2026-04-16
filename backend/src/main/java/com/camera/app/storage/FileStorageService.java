package com.camera.app.storage;

import java.io.InputStream;

/**
 * 文件存储服务接口（当前后端：MinIO）
 * 只暴露 upload / download / delete，不涉及文件执行或扫描逻辑。
 */
public interface FileStorageService {

    /**
     * 上传文件到对象存储
     *
     * @param objectKey   对象路径，如 pocs/{uuid}/{filename}
     * @param inputStream 文件输入流
     * @param size        文件字节大小
     * @param contentType MIME 类型
     */
    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * 从对象存储下载文件
     *
     * @param objectKey 对象路径
     * @return 文件输入流（调用方负责关闭）
     */
    InputStream download(String objectKey);

    /**
     * 从对象存储删除文件（逻辑错误时静默记录日志，不抛出异常）
     *
     * @param objectKey 对象路径
     */
    void delete(String objectKey);
}
