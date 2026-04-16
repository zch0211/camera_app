package com.camera.app.poc.repository;

import com.camera.app.poc.entity.Poc;
import com.camera.app.poc.entity.PocStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PocRepository extends JpaRepository<Poc, Long>, JpaSpecificationExecutor<Poc> {

    /**
     * 按 status 查找——用于判断 ACTIVE 记录是否存在
     */
    boolean existsByIdAndStatus(Long id, PocStatus status);
}
