package com.camera.app.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "discovery_results")
public class DiscoveryResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 64)
    private String ip;

    @Column(length = 32)
    private String mac;

    @Column(length = 255)
    private String hostname;

    @Column(name = "vendor_hint", length = 255)
    private String vendorHint;

    /** JSON array of open port numbers */
    @Column(name = "open_ports", columnDefinition = "TEXT")
    private String openPorts;

    /** Comma-separated protocol labels */
    @Column(length = 512)
    private String protocols;

    @Column(name = "device_type_candidate", length = 64)
    private String deviceTypeCandidate;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, columnDefinition = "varchar(32)")
    private DiscoverySourceType sourceType = DiscoverySourceType.SCAN;

    /** JSON evidence blob */
    @Column(name = "raw_evidence", columnDefinition = "TEXT")
    private String rawEvidence;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "linked_asset_id")
    private Long linkedAssetId;

    @Column(nullable = false)
    private boolean managed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
