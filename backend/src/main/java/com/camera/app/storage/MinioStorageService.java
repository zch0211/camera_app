package com.camera.app.storage;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.config.MinioProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
                log.info("MinIO bucket created: {}", minioProperties.getBucket());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            log.debug("Uploaded object to MinIO: {}", objectKey);
        } catch (Exception e) {
            log.error("MinIO upload failed for key={}", objectKey, e);
            throw new BusinessException(500, "文件上传失败，请稍后重试");
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new BusinessException(404, "文件在对象存储中不存在，key=" + objectKey);
            }
            log.error("MinIO download failed for key={}", objectKey, e);
            throw new BusinessException(500, "文件下载失败，请稍后重试");
        } catch (Exception e) {
            log.error("MinIO download failed for key={}", objectKey, e);
            throw new BusinessException(500, "文件下载失败，请稍后重试");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
            log.debug("Deleted object from MinIO: {}", objectKey);
        } catch (Exception e) {
            // 删除失败不中断业务，记录日志即可
            log.warn("Failed to delete MinIO object: {}, error: {}", objectKey, e.getMessage());
        }
    }
}
