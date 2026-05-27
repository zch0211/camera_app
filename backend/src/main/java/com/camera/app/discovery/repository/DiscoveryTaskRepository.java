package com.camera.app.discovery.repository;

import com.camera.app.discovery.entity.DiscoveryTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryTaskRepository extends JpaRepository<DiscoveryTask, Long> {

    Page<DiscoveryTask> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
