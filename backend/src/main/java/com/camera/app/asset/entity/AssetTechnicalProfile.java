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
@Table(name = "asset_technical_profiles")
public class AssetTechnicalProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, unique = true)
    private Long assetId;

    @Column(columnDefinition = "TEXT")
    private String openPorts;

    @Column(columnDefinition = "TEXT")
    private String protocols;

    @Column(name = "service_banner", length = 512)
    private String serviceBanner;

    @Column(name = "web_title", length = 256)
    private String webTitle;

    @Column(name = "firmware_version", length = 128)
    private String firmwareVersion;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "mac_address", length = 64)
    private String macAddress;

    @Column(name = "vendor_hint", length = 128)
    private String vendorHint;

    @Column(name = "last_fingerprint_at")
    private LocalDateTime lastFingerprintAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
