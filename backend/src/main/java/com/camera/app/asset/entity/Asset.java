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
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String ip;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 64)
    private String brand;

    @Column(length = 64)
    private String model;

    @Column(length = 256)
    private String location;

    @Column(nullable = false)
    private boolean online = false;

    @Column(name = "risk_score")
    private Integer riskScore = 0;

    @Column(name = "org_id")
    private Long orgId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
