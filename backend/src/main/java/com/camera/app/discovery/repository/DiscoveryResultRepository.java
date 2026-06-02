package com.camera.app.discovery.repository;

import com.camera.app.discovery.entity.DiscoveryLevel;
import com.camera.app.discovery.entity.DiscoveryResult;
import com.camera.app.discovery.entity.DiscoverySourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscoveryResultRepository extends JpaRepository<DiscoveryResult, Long> {

    /** Per-task filtered page (requires taskId). */
    @Query("""
        SELECT r FROM DiscoveryResult r WHERE r.taskId = :taskId
          AND (:managed IS NULL OR r.managed = :managed)
          AND (:sourceType IS NULL OR r.sourceType = :sourceType)
          AND (:deviceType IS NULL OR r.deviceTypeCandidate = :deviceType)
          AND (:keyword IS NULL
               OR r.ip LIKE %:keyword%
               OR r.hostname LIKE %:keyword%
               OR r.vendorHint LIKE %:keyword%)
        ORDER BY r.firstSeenAt DESC
        """)
    Page<DiscoveryResult> findByTaskIdWithFilters(
            @Param("taskId") Long taskId,
            @Param("managed") Boolean managed,
            @Param("sourceType") DiscoverySourceType sourceType,
            @Param("deviceType") String deviceType,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** Cross-task overview (taskId optional). */
    @Query("""
        SELECT r FROM DiscoveryResult r
        WHERE (:taskId IS NULL OR r.taskId = :taskId)
          AND (:managed IS NULL OR r.managed = :managed)
          AND (:sourceType IS NULL OR r.sourceType = :sourceType)
          AND (:deviceType IS NULL OR r.deviceTypeCandidate = :deviceType)
          AND (:discoveryLevel IS NULL OR r.discoveryLevel = :discoveryLevel)
          AND (:keyword IS NULL
               OR r.ip LIKE %:keyword%
               OR r.hostname LIKE %:keyword%
               OR r.vendorHint LIKE %:keyword%)
        ORDER BY r.lastSeenAt DESC
        """)
    Page<DiscoveryResult> findAllWithFilters(
            @Param("taskId") Long taskId,
            @Param("managed") Boolean managed,
            @Param("sourceType") DiscoverySourceType sourceType,
            @Param("deviceType") String deviceType,
            @Param("discoveryLevel") DiscoveryLevel discoveryLevel,
            @Param("keyword") String keyword,
            Pageable pageable);

    long countByTaskId(Long taskId);

    List<DiscoveryResult> findByIdIn(List<Long> ids);

    Optional<DiscoveryResult> findByTaskIdAndIp(Long taskId, String ip);

    boolean existsByIpAndTaskId(String ip, Long taskId);

    Optional<DiscoveryResult> findTopByIpOrderByLastSeenAtDesc(String ip);
}
