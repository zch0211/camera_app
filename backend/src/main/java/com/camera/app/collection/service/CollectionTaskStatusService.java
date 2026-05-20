package com.camera.app.collection.service;

import com.camera.app.collection.entity.AssetCollectionTask;
import com.camera.app.collection.entity.CollectionTaskStatus;
import com.camera.app.collection.repository.AssetCollectionTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Thin transactional helper for task status transitions.
 * Kept in a separate bean so @Async threads can call @Transactional methods
 * through the Spring proxy without self-invocation bypass.
 */
@Service
@RequiredArgsConstructor
public class CollectionTaskStatusService {

    private final AssetCollectionTaskRepository taskRepository;

    @Transactional
    public void markRunning(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(CollectionTaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    @Transactional
    public void markSuccess(Long taskId, String summary, String partialPluginError) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(CollectionTaskStatus.SUCCESS);
            task.setFinishedAt(LocalDateTime.now());
            task.setSuccess(true);
            task.setSummary(summary);
            task.setWritebackApplied(true);
            if (partialPluginError != null) {
                task.setErrorMessage(partialPluginError);
            }
            taskRepository.save(task);
        });
    }

    @Transactional
    public void markFailed(Long taskId, String errorMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(CollectionTaskStatus.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setSuccess(false);
            task.setErrorMessage(errorMessage);
            taskRepository.save(task);
        });
    }

    @Transactional
    public AssetCollectionTask loadTask(Long taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }
}
