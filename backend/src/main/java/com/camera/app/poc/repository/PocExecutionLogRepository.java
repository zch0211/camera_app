package com.camera.app.poc.repository;

import com.camera.app.poc.entity.PocExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;

public interface PocExecutionLogRepository
        extends JpaRepository<PocExecutionLog, Long>, JpaSpecificationExecutor<PocExecutionLog> {

    long countByCreatedAtAfter(LocalDateTime since);
}
