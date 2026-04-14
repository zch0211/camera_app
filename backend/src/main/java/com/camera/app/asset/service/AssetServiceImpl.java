package com.camera.app.asset.service;

import com.camera.app.asset.dto.AssetCreateRequest;
import com.camera.app.asset.dto.AssetResponse;
import com.camera.app.asset.dto.AssetUpdateRequest;
import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<AssetResponse> listAssets(String keyword, String brand, String model,
                                                Boolean online, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Specification<Asset> spec = buildSpec(keyword, brand, model, online);
        return new PageResult<>(assetRepository.findAll(spec, pageable).map(AssetResponse::new));
    }

    @Override
    @Transactional(readOnly = true)
    public AssetResponse getAsset(Long id) {
        return new AssetResponse(findById(id));
    }

    @Override
    public AssetResponse createAsset(AssetCreateRequest request) {
        if (assetRepository.existsByIp(request.getIp())) {
            throw new BusinessException(409, "IP 已存在: " + request.getIp());
        }
        Asset asset = new Asset();
        asset.setIp(request.getIp());
        asset.setName(request.getName());
        asset.setBrand(request.getBrand());
        asset.setModel(request.getModel());
        asset.setLocation(request.getLocation());
        asset.setOnline(request.isOnline());
        asset.setRiskScore(request.getRiskScore() != null ? request.getRiskScore() : 0);
        asset.setOrgId(request.getOrgId());
        return new AssetResponse(assetRepository.save(asset));
    }

    @Override
    public AssetResponse updateAsset(Long id, AssetUpdateRequest request) {
        Asset asset = findById(id);
        if (StringUtils.hasText(request.getIp()) && !request.getIp().equals(asset.getIp())) {
            if (assetRepository.existsByIpAndIdNot(request.getIp(), id)) {
                throw new BusinessException(409, "IP 已存在: " + request.getIp());
            }
            asset.setIp(request.getIp());
        }
        if (StringUtils.hasText(request.getName())) {
            asset.setName(request.getName());
        }
        if (request.getBrand() != null) {
            asset.setBrand(request.getBrand());
        }
        if (request.getModel() != null) {
            asset.setModel(request.getModel());
        }
        if (request.getLocation() != null) {
            asset.setLocation(request.getLocation());
        }
        if (request.getOnline() != null) {
            asset.setOnline(request.getOnline());
        }
        if (request.getRiskScore() != null) {
            asset.setRiskScore(request.getRiskScore());
        }
        if (request.getOrgId() != null) {
            asset.setOrgId(request.getOrgId());
        }
        return new AssetResponse(assetRepository.save(asset));
    }

    @Override
    public void deleteAsset(Long id) {
        Asset asset = findById(id);
        assetRepository.delete(asset);
    }

    private Asset findById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + id));
    }

    private Specification<Asset> buildSpec(String keyword, String brand, String model, Boolean online) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("ip")), like)
                ));
            }
            if (StringUtils.hasText(brand)) {
                predicates.add(cb.equal(root.get("brand"), brand));
            }
            if (StringUtils.hasText(model)) {
                predicates.add(cb.equal(root.get("model"), model));
            }
            if (online != null) {
                predicates.add(cb.equal(root.get("online"), online));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
