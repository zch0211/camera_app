package com.camera.app.collection.rules;

import com.camera.app.asset.entity.AssetInferenceCandidate;
import com.camera.app.asset.entity.InferenceCandidateSourceType;
import com.camera.app.asset.repository.AssetInferenceCandidateRepository;
import com.camera.app.collection.plugin.ProbeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTypeRuleEngine {

    private final List<DeviceTypeRule> rules;
    private final AssetInferenceCandidateRepository candidateRepository;

    private static final BigDecimal MIN_CONFIDENCE_THRESHOLD = new BigDecimal("0.300");

    @Transactional
    public List<CategoryDetectionResult> detect(Long assetId, List<ProbeResult> probeResults) {
        RuleContext ctx = RuleContext.from(assetId, probeResults);
        Map<DeviceCategory, CategoryDetectionResult> best = new HashMap<>();

        for (DeviceTypeRule rule : rules) {
            BigDecimal score = rule.evaluate(ctx);
            if (score.compareTo(MIN_CONFIDENCE_THRESHOLD) < 0) continue;

            String reason = rule.buildReason(ctx);
            DeviceCategory cat = rule.targetCategory();
            CategoryDetectionResult existing = best.get(cat);
            if (existing == null || score.compareTo(existing.getConfidence()) > 0) {
                best.put(cat, CategoryDetectionResult.builder()
                        .category(cat)
                        .confidence(score)
                        .reason(reason)
                        .supportingFacts(List.of(rule.getName()))
                        .build());
            }
        }

        List<CategoryDetectionResult> results = new ArrayList<>(best.values());
        results.sort(Comparator.comparing(CategoryDetectionResult::getConfidence).reversed());

        // Persist as inference candidates (upsert by assetId + fieldName + candidateValue)
        for (CategoryDetectionResult det : results) {
            upsertCandidate(assetId, det);
        }

        return results;
    }

    private void upsertCandidate(Long assetId, CategoryDetectionResult det) {
        String fieldName = "deviceCategory";
        String value = det.getCategory().name();
        candidateRepository.findByAssetIdAndFieldNameAndCandidateValue(assetId, fieldName, value)
                .ifPresentOrElse(
                        existing -> {
                            existing.setConfidence(det.getConfidence());
                            existing.setReason(det.getReason());
                            candidateRepository.save(existing);
                        },
                        () -> {
                            var c = new AssetInferenceCandidate();
                            c.setAssetId(assetId);
                            c.setFieldName(fieldName);
                            c.setCandidateValue(value);
                            c.setConfidence(det.getConfidence());
                            c.setReason(det.getReason());
                            c.setSourceType(InferenceCandidateSourceType.RULE);
                            candidateRepository.save(c);
                        }
                );
    }
}
