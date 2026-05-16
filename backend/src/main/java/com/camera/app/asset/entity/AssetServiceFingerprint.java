package com.camera.app.asset.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "asset_service_fingerprints",
        uniqueConstraints = @UniqueConstraint(name = "uk_asf_asset_port", columnNames = {"asset_id", "port"}))
public class AssetServiceFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "transport_protocol", length = 16, nullable = false)
    private String transportProtocol = "TCP";

    @Column(name = "application_protocol", length = 32, nullable = false)
    private String applicationProtocol = "UNKNOWN";

    @Column(length = 16)
    private String scheme;

    @Column(name = "service_banner", length = 512)
    private String serviceBanner;

    @Column(name = "web_title", length = 256)
    private String webTitle;

    @Column(name = "server_header", length = 256)
    private String serverHeader;

    @Column(name = "vendor_hint", length = 256)
    private String vendorHint;

    @Column(name = "product_hint", length = 128)
    private String productHint;

    // OPEN / CLOSED / UNKNOWN
    @Column(length = 16, nullable = false)
    private String status = "UNKNOWN";

    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;

    @Column(name = "last_task_id")
    private Long lastTaskId;

    @Column(name = "raw_summary", columnDefinition = "TEXT")
    private String rawSummary;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
