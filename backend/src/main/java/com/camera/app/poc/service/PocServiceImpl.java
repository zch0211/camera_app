package com.camera.app.poc.service;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocListItemResponse;
import com.camera.app.poc.dto.PocResponse;
import com.camera.app.poc.dto.PocUpdateRequest;
import com.camera.app.poc.entity.*;
import com.camera.app.poc.repository.PocRepository;
import com.camera.app.storage.FileStorageService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PocServiceImpl implements PocService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    private final PocRepository pocRepository;
    private final FileStorageService fileStorageService;

    // ─── List ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<PocListItemResponse> listPocs(
            String keyword, String severity, Boolean enabled,
            String language, String targetType,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Specification<Poc> spec = buildSpec(keyword, severity, enabled, language, targetType);
        return new PageResult<>(pocRepository.findAll(spec, pageable).map(PocListItemResponse::new));
    }

    // ─── Get ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PocResponse getPoc(Long id) {
        return new PocResponse(findActiveById(id));
    }

    // ─── Upload ───────────────────────────────────────────────────────────────

    @Override
    public PocResponse uploadPoc(
            MultipartFile file,
            String name,
            String description,
            String cveId,
            String severityStr,
            String languageStr,
            String targetTypeStr,
            String vendor,
            String protocolStr,
            String entryPoint,
            boolean enabled,
            String createdBy) {

        // ── 基础校验 ──────────────────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "POC 名称不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "文件超过 50 MB 限制");
        }

        Severity severity = parseEnum(Severity.class, severityStr, "severity");
        TargetType targetType = parseEnum(TargetType.class, targetTypeStr, "targetType");
        Language language = resolveLanguage(languageStr, file.getOriginalFilename());
        Protocol protocol = parseEnumOrNull(Protocol.class, protocolStr);

        // ── 读取字节（计算 SHA-256）─────────────────────────────────────
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(400, "读取文件失败: " + e.getMessage());
        }
        String sha256 = calculateSha256(bytes);

        // ── 构建 object key 并上传 ────────────────────────────────────────
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "poc-file";
        String objectKey = buildObjectKey(originalFilename);

        fileStorageService.upload(
                objectKey,
                new ByteArrayInputStream(bytes),
                bytes.length,
                file.getContentType());

        // ── 保存元数据 ─────────────────────────────────────────────────────
        Poc poc = new Poc();
        poc.setName(name.trim());
        poc.setDescription(description);
        poc.setCveId(StringUtils.hasText(cveId) ? cveId.trim() : null);
        poc.setSeverity(severity);
        poc.setLanguage(language);
        poc.setTargetType(targetType);
        poc.setVendor(StringUtils.hasText(vendor) ? vendor.trim() : null);
        poc.setProtocol(protocol);
        poc.setEntryPoint(StringUtils.hasText(entryPoint) ? entryPoint.trim() : null);
        poc.setObjectKey(objectKey);
        poc.setOriginalFilename(originalFilename);
        poc.setContentType(file.getContentType());
        poc.setFileSize((long) bytes.length);
        poc.setFileSha256(sha256);
        poc.setEnabled(enabled);
        poc.setStatus(PocStatus.ACTIVE);
        poc.setCreatedBy(createdBy);

        return new PocResponse(pocRepository.save(poc));
    }

    // ─── Download ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PocDownloadResult downloadPoc(Long id) {
        Poc poc = findActiveById(id);
        InputStream stream = fileStorageService.download(poc.getObjectKey());
        return new PocDownloadResult(
                poc.getOriginalFilename(),
                poc.getContentType() != null ? poc.getContentType() : "application/octet-stream",
                poc.getFileSize(),
                stream);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Override
    public PocResponse updatePoc(Long id, PocUpdateRequest request) {
        Poc poc = findActiveById(id);

        if (StringUtils.hasText(request.getName())) {
            poc.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            poc.setDescription(request.getDescription());
        }
        if (request.getCveId() != null) {
            poc.setCveId(StringUtils.hasText(request.getCveId()) ? request.getCveId().trim() : null);
        }
        if (StringUtils.hasText(request.getSeverity())) {
            poc.setSeverity(parseEnum(Severity.class, request.getSeverity(), "severity"));
        }
        if (StringUtils.hasText(request.getLanguage())) {
            poc.setLanguage(parseEnum(Language.class, request.getLanguage(), "language"));
        }
        if (StringUtils.hasText(request.getTargetType())) {
            poc.setTargetType(parseEnum(TargetType.class, request.getTargetType(), "targetType"));
        }
        if (request.getVendor() != null) {
            poc.setVendor(StringUtils.hasText(request.getVendor()) ? request.getVendor().trim() : null);
        }
        if (StringUtils.hasText(request.getProtocol())) {
            poc.setProtocol(parseEnum(Protocol.class, request.getProtocol(), "protocol"));
        }
        if (request.getEntryPoint() != null) {
            poc.setEntryPoint(StringUtils.hasText(request.getEntryPoint()) ? request.getEntryPoint().trim() : null);
        }
        if (request.getEnabled() != null) {
            poc.setEnabled(request.getEnabled());
        }

        return new PocResponse(pocRepository.save(poc));
    }

    // ─── Delete（逻辑删除）────────────────────────────────────────────────────

    @Override
    public void deletePoc(Long id) {
        Poc poc = findActiveById(id);
        poc.setStatus(PocStatus.DELETED);
        poc.setEnabled(false);
        pocRepository.save(poc);
        // 同步删除 MinIO 对象（失败时静默记录，不阻断事务）
        try {
            fileStorageService.delete(poc.getObjectKey());
        } catch (Exception e) {
            log.warn("MinIO delete failed for poc id={}, key={}", id, poc.getObjectKey(), e);
        }
    }

    // ─── 私有辅助方法 ──────────────────────────────────────────────────────────

    private Poc findActiveById(Long id) {
        Poc poc = pocRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "POC 不存在，id=" + id));
        if (poc.getStatus() == PocStatus.DELETED) {
            throw new BusinessException(404, "POC 不存在，id=" + id);
        }
        return poc;
    }

    private Specification<Poc> buildSpec(
            String keyword, String severity, Boolean enabled,
            String language, String targetType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 只查非逻辑删除的记录
            predicates.add(cb.notEqual(root.get("status"), PocStatus.DELETED));

            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("cveId")), like)
                ));
            }
            if (StringUtils.hasText(severity)) {
                try {
                    predicates.add(cb.equal(root.get("severity"), Severity.valueOf(severity.toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (StringUtils.hasText(language)) {
                try {
                    predicates.add(cb.equal(root.get("language"), Language.valueOf(language.toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }
            if (StringUtils.hasText(targetType)) {
                try {
                    predicates.add(cb.equal(root.get("targetType"), TargetType.valueOf(targetType.toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String buildObjectKey(String originalFilename) {
        return "pocs/" + UUID.randomUUID() + "/" + originalFilename;
    }

    private String calculateSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new BusinessException(500, "SHA-256 计算失败");
        }
    }

    private Language resolveLanguage(String languageStr, String filename) {
        if (StringUtils.hasText(languageStr)) {
            return parseEnumOrNull(Language.class, languageStr);
        }
        if (!StringUtils.hasText(filename)) return Language.OTHER;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".py"))                      return Language.PYTHON;
        if (lower.endsWith(".java"))                    return Language.JAVA;
        if (lower.endsWith(".go"))                      return Language.GO;
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return Language.SHELL;
        return Language.OTHER;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, fieldName + " 不能为空");
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "非法的 " + fieldName + " 值: " + value);
        }
    }

    private <E extends Enum<E>> E parseEnumOrNull(Class<E> enumClass, String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
