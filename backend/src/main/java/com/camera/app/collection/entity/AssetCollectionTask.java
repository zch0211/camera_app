package com.camera.app.collection.entity;

import com.camera.app.collection.entity.TaskPreset;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "asset_collection_tasks")
public class AssetCollectionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, columnDefinition = "varchar(32)")
    private CollectionTaskType taskType = CollectionTaskType.LIGHTWEIGHT_PROBE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private TaskPreset preset = TaskPreset.CUSTOM;

    @Column(name = "enabled_plugins", columnDefinition = "TEXT")
    private String enabledPlugins;   // JSON array of plugin names, null = all resolved by preset

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private CollectionTaskStatus status = CollectionTaskStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, columnDefinition = "varchar(32)")
    private TriggerType triggerType = TriggerType.MANUAL;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private Boolean success;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "writeback_applied", nullable = false)
    private boolean writebackApplied = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
