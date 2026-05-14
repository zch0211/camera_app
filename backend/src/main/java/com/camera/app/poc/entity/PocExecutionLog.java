package com.camera.app.poc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "poc_execution_logs")
public class PocExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poc_id", nullable = false)
    private Long pocId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "executed_by", nullable = false, length = 100)
    private String executedBy;

    private Boolean success;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String stdout;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String stderr;

    @Column(nullable = false)
    private boolean truncated;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
