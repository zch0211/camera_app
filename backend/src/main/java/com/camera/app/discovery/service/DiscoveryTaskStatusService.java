package com.camera.app.discovery.service;

import com.camera.app.discovery.entity.DiscoveryStatus;
import com.camera.app.discovery.repository.DiscoveryTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Isolated transactional helper for discovery task status transitions.
 * Separate bean so @Async threads can call @Transactional methods
 * through the Spring proxy (avoids self-invocation proxy bypass).
 *
 * State machine: PENDING → RUNNING → SUCCESS / FAILED / CANCELED
 * Stop API may set CANCELED at any time; all transitions check current state
 * before overwriting to avoid races.
 */
@Service
@RequiredArgsConstructor
public class DiscoveryTaskStatusService {

    private final DiscoveryTaskRepository taskRepository;

    /** PENDING → RUNNING; skips if task is already CANCELED (stop called early). */
    @Transactional
    public void markRunning(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == DiscoveryStatus.PENDING) {
                task.setStatus(DiscoveryStatus.RUNNING);
                task.setStartedAt(LocalDateTime.now());
                taskRepository.save(task);
            }
        });
    }

    /** RUNNING → SUCCESS; skips if task has been CANCELED externally. */
    @Transactional
    public void markSuccess(Long taskId, String summary, int aliveCount, int candidateCount) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == DiscoveryStatus.RUNNING) {
                task.setStatus(DiscoveryStatus.SUCCESS);
                task.setFinishedAt(LocalDateTime.now());
                task.setSummary(summary);
                task.setAliveCount(aliveCount);
                task.setCandidateCount(candidateCount);
                task.setDiscoveredCount(aliveCount + candidateCount);
                taskRepository.save(task);
            }
        });
    }

    /** RUNNING → FAILED; skips if task has been CANCELED externally. */
    @Transactional
    public void markFailed(Long taskId, String errorMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == DiscoveryStatus.RUNNING) {
                task.setStatus(DiscoveryStatus.FAILED);
                task.setFinishedAt(LocalDateTime.now());
                task.setErrorMessage(errorMessage);
                taskRepository.save(task);
            }
        });
    }

    /**
     * Update alive/candidate counts and summary while task is RUNNING.
     * Called periodically during scan to give users real-time progress.
     */
    @Transactional
    public void updateProgress(Long taskId, int aliveCount, int candidateCount, String summary) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == DiscoveryStatus.RUNNING) {
                task.setAliveCount(aliveCount);
                task.setCandidateCount(candidateCount);
                task.setDiscoveredCount(aliveCount + candidateCount);
                task.setSummary(summary);
                taskRepository.save(task);
            }
        });
    }

    /**
     * Final update after a CANCELED task's scan loop exits.
     * Updates counts and summary without changing the CANCELED status
     * (which was already set by the stop API).
     */
    @Transactional
    public void finalizeCanceled(Long taskId, int aliveCount, int candidateCount, String summary) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == DiscoveryStatus.CANCELED) {
                task.setAliveCount(aliveCount);
                task.setCandidateCount(candidateCount);
                task.setDiscoveredCount(aliveCount + candidateCount);
                task.setSummary(summary);
                taskRepository.save(task);
            }
        });
    }

    @Transactional(readOnly = true)
    public boolean isCanceled(Long taskId) {
        return taskRepository.findById(taskId)
                .map(t -> t.getStatus() == DiscoveryStatus.CANCELED)
                .orElse(false);
    }
}
