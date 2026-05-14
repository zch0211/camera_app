package com.camera.app.poc.repository;

import com.camera.app.poc.entity.Poc;
import com.camera.app.poc.entity.PocStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PocRepository extends JpaRepository<Poc, Long>, JpaSpecificationExecutor<Poc> {

    boolean existsByIdAndStatus(Long id, PocStatus status);

    long countByStatusNot(PocStatus status);

    long countByEnabledTrueAndStatusNot(PocStatus status);
}
