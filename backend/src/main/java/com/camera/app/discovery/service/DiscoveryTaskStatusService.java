package com.camera.app.discovery.service;

import com.camera.app.discovery.entity.DiscoveryStatus;
import com.camera.app.discovery.repository.DiscoveryTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Isolated transactional helper for discovery task status transitions.
 * Kept in its own bean so @Async threads can call @Transactional methods
 * through the Spring proxy (avoids self-invocation proxy bypass).
 */
@Service
@RequiredArgsConstructor
public class DiscoveryTaskStatusService {

    private final DiscoveryTaskRepository taskRepository;

    @Transactional
    public void markRunning(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(DiscoveryStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    @Transactional
    public void markSuccess(Long taskId, String summary, int discoveredCount) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(DiscoveryStatus.SUCCESS);
            task.setFinishedAt(LocalDateTime.now());
            task.setSummary(summary);
            task.setDiscoveredCount(discoveredCount);
            taskRepository.save(task);
        });
    }

    @Transactional
    public void markFailed(Long taskId, String errorMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(DiscoveryStatus.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(errorMessage);
            taskRepository.save(task);
        });
    }
}
