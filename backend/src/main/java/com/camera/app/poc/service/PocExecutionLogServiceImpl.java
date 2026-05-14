package com.camera.app.poc.service;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocExecutionLogResponse;
import com.camera.app.poc.dto.PocExecutionLogSummary;
import com.camera.app.poc.entity.PocExecutionLog;
import com.camera.app.poc.repository.PocExecutionLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PocExecutionLogServiceImpl implements PocExecutionLogService {

    private final PocExecutionLogRepository repository;

    @Override
    @Transactional
    public PocExecutionLog save(PocExecutionLog log) {
        return repository.save(log);
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
        return new PocExecutionLogResponse(log);
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
