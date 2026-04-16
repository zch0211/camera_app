package com.camera.app.poc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pocs")
public class Poc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cve_id", length = 64)
    private String cveId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(16)")
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(16)")
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, columnDefinition = "varchar(16)")
    private TargetType targetType;

    @Column(length = 100)
    private String vendor;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(16)")
    private Protocol protocol;

    /**
     * 预留字段：供后续扫描执行器使用的入口点（如函数名、类名、脚本路径等）
     */
    @Column(name = "entry_point", length = 500)
    private String entryPoint;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(16)")
    private PocStatus status = PocStatus.ACTIVE;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
