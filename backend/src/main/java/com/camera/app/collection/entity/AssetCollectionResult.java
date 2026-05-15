package com.camera.app.collection.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "asset_collection_results")
public class AssetCollectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "probe_type", nullable = false, columnDefinition = "varchar(32)")
    private ProbeType probeType;

    @Column(nullable = false)
    private boolean success = false;

    @Column(name = "target_host", length = 128)
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "protocol_hint", length = 32)
    private String protocolHint;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "parsed_data", columnDefinition = "TEXT")
    private String parsedData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
