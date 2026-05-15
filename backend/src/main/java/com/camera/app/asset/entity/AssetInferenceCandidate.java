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
@Table(name = "asset_inference_candidates")
public class AssetInferenceCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "candidate_value", nullable = false, length = 512)
    private String candidateValue;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, columnDefinition = "varchar(32)")
    private InferenceCandidateSourceType sourceType = InferenceCandidateSourceType.MANUAL;

    @Column(nullable = false)
    private boolean confirmed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
