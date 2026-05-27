package com.camera.app.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "discovery_tasks")
public class DiscoveryTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, columnDefinition = "varchar(32)")
    private DiscoveryTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private DiscoveryStatus status = DiscoveryStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private DiscoveryPreset preset = DiscoveryPreset.FULL_DISCOVERY;

    @Column(name = "target_scope", nullable = false, length = 512)
    private String targetScope;

    /** JSON array of ports, null = use preset defaults */
    @Column(columnDefinition = "TEXT")
    private String ports;

    @Column(name = "timeout_millis", nullable = false)
    private int timeoutMillis = 2000;

    @Column(name = "sniff_duration_sec", nullable = false)
    private int sniffDurationSec = 60;

    @Column(name = "network_interface", length = 64)
    private String networkInterface;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "discovered_count", nullable = false)
    private int discoveredCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
