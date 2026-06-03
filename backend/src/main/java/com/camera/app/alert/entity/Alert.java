package com.camera.app.alert.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id")
    private Long assetId;

    /** Snapshot of asset.name at creation time; retained even after asset deletion. */
    @Column(name = "asset_name_snapshot", length = 128)
    private String assetNameSnapshot;

    @Column(name = "asset_ip_snapshot", length = 64)
    private String assetIpSnapshot;

    @Column(name = "asset_type_snapshot", length = 32)
    private String assetTypeSnapshot;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, columnDefinition = "varchar(32)")
    private AlertSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(16)")
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private AlertStatus status = AlertStatus.NEW;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    @Column(name = "rule_name", length = 128)
    private String ruleName;

    @Column(length = 128)
    private String fingerprint;

    @Column(name = "first_triggered_at", nullable = false)
    private LocalDateTime firstTriggeredAt;

    @Column(name = "last_triggered_at", nullable = false)
    private LocalDateTime lastTriggeredAt;

    @Column(name = "trigger_count", nullable = false)
    private int triggerCount = 1;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
