package com.camera.app.alert.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "alert_evidences")
public class AlertEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, columnDefinition = "varchar(32)")
    private AlertEvidenceType evidenceType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "field_name", length = 128)
    private String fieldName;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 5, scale = 3)
    private BigDecimal confidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
