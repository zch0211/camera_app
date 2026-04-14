package com.camera.app.asset.repository;

import com.camera.app.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    boolean existsByIp(String ip);

    boolean existsByIpAndIdNot(String ip, Long id);
}
