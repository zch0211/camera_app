package com.camera.app.poc.service;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.ArtifactInfo;
import com.camera.app.poc.dto.PocExecutionLogResponse;
import com.camera.app.poc.dto.PocExecutionLogSummary;
import com.camera.app.poc.entity.PocExecutionLog;
import com.camera.app.poc.repository.PocExecutionLogRepository;
import com.camera.app.storage.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PocExecutionLogServiceImpl implements PocExecutionLogService {

    private final PocExecutionLogRepository repository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PocExecutionLog save(PocExecutionLog log) {
        return repository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<PocExecutionLog> findLogById(Long id) {
        return repository.findById(id);
    }

    @Override
    public PageResult<PocExecutionLogSummary> list(Long pocId, Long assetId, Boolean success, int page, int size) {
        Specification<PocExecutionLog> spec = buildSpec(pocId, assetId, success);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PageResult<>(repository.findAll(spec, pageRequest).map(PocExecutionLogSummary::new));
    }

    @Override
    public PocExecutionLogResponse getById(Long id) {
        PocExecutionLog log = repository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "执行记录不存在，id=" + id));
        List<ArtifactInfo> artifacts = rebuildArtifacts(id, log.getArtifactSummary());
        return new PocExecutionLogResponse(log, artifacts);
    }

    /**
     * Reconstructs ArtifactInfo list from the persisted JSON summary, adding download/preview
     * URLs so the detail view can render attachments without needing the in-memory execution context.
     */
    private List<ArtifactInfo> rebuildArtifacts(Long logId, String artifactSummary) {
        if (artifactSummary == null || artifactSummary.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    artifactSummary, new TypeReference<>() {});
            List<ArtifactInfo> result = new ArrayList<>(raw.size());
            for (Map<String, Object> m : raw) {
                String name = (String) m.get("name");
                if (name == null || name.isBlank()) continue;
                String type = (String) m.getOrDefault("type", "FILE");
                String mimeType = (String) m.getOrDefault("mimeType", "application/octet-stream");
                Object sizeObj = m.get("sizeBytes");
                Long sizeBytes = sizeObj instanceof Number n ? n.longValue() : null;
                String objectKey = (String) m.get("objectKey");
                boolean isImage = "IMAGE".equals(type);
                String base = "/api/v1/poc-executions/" + logId
                        + "/artifacts/" + URLEncoder.encode(name, StandardCharsets.UTF_8);
                result.add(ArtifactInfo.builder()
                        .name(name)
                        .type(type)
                        .mimeType(mimeType)
                        .sizeBytes(sizeBytes)
                        .objectKey(objectKey)
                        .path(objectKey)
                        .previewable(isImage)
                        .downloadable(true)
                        .downloadUrl(base + "/download")
                        .previewUrl(isImage ? base + "/preview" : null)
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("[POC-LOG] Failed to parse artifactSummary for execution log {}: {}", logId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public PocDownloadResult downloadArtifact(Long logId, String name) {
        PocExecutionLog log = repository.findById(logId)
                .orElseThrow(() -> new BusinessException(404, "执行记录不存在，id=" + logId));

        String summary = log.getArtifactSummary();
        if (summary == null || summary.isBlank()) {
            throw new BusinessException(404, "执行记录无附件");
        }

        try {
            List<Map<String, Object>> artifacts = objectMapper.readValue(
                    summary, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> artifact = artifacts.stream()
                    .filter(a -> name.equals(a.get("name")))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(404, "附件不存在: " + name));

            String objectKey = (String) artifact.get("objectKey");
            String contentType = (String) artifact.getOrDefault("mimeType", "application/octet-stream");
            Object sizeObj = artifact.get("sizeBytes");
            Long fileSize = sizeObj instanceof Number ? ((Number) sizeObj).longValue() : null;

            InputStream is = fileStorageService.download(objectKey);
            return new PocDownloadResult(name, contentType, fileSize, is);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "解析附件元数据失败: " + e.getMessage());
        }
    }

    private Specification<PocExecutionLog> buildSpec(Long pocId, Long assetId, Boolean success) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pocId != null) predicates.add(cb.equal(root.get("pocId"), pocId));
            if (assetId != null) predicates.add(cb.equal(root.get("assetId"), assetId));
            if (success != null) predicates.add(cb.equal(root.get("success"), success));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
