package com.camera.app.asset.entity;

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
@Table(name = "asset_evidences")
public class AssetEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "field_value", length = 512)
    private String fieldValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, columnDefinition = "varchar(32)")
    private EvidenceSourceType sourceType = EvidenceSourceType.MANUAL;

    @Column(name = "raw_evidence", columnDefinition = "TEXT")
    private String rawEvidence;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence = BigDecimal.ONE;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(length = 512)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
