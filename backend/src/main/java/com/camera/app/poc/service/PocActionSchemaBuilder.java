package com.camera.app.poc.service;

import com.camera.app.poc.dto.PocAction;
import com.camera.app.poc.entity.Language;
import com.camera.app.poc.entity.Poc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 POC 元数据 + Python 脚本内容识别动作（action）列表。
 *
 * 动作定义的单一事实来源是 {@link StandardActions}，本类负责"识别"逻辑：
 * 按 StandardActions.ALL 顺序扫描脚本中是否含对应 cliFlag；
 * 找到的第一个动作标记为 defaultAction=true；
 * 识别不到任何 flag 则回退到仅含 CHECK_VULN 的默认列表。
 */
@Slf4j
@Component
public class PocActionSchemaBuilder {

    /**
     * 构建 POC 的动作列表。
     *
     * @param poc           POC 元数据
     * @param scriptContent Python 脚本文本（null 时跳过内容识别，直接回退）
     * @return 动作列表，至少含 1 个元素，第一个 defaultAction=true
     */
    public List<PocAction> buildActions(Poc poc, String scriptContent) {
        if (poc.getLanguage() == Language.PYTHON && scriptContent != null) {
            List<PocAction> detected = detectFromContent(poc.getId(), scriptContent);
            if (!detected.isEmpty()) {
                return detected;
            }
        }
        log.debug("[ActionBuilder] pocId={} fallback to default CHECK_VULN", poc.getId());
        return fallback();
    }

    /** 根据 action key 返回其预期 outputType 字符串，委托 StandardActions。 */
    public String getOutputType(String actionKey) {
        return StandardActions.outputType(actionKey);
    }

    /** 根据 action key 返回其显示 label，委托 StandardActions。 */
    public String getLabel(String actionKey) {
        return StandardActions.label(actionKey);
    }

    // ─── private ──────────────────────────────────────────────────────────────

    private List<PocAction> detectFromContent(Long pocId, String content) {
        List<PocAction> result = new ArrayList<>();
        boolean first = true;
        for (StandardActions.ActionDef def : StandardActions.ALL) {
            if (content.contains(def.cliFlag())) {
                result.add(toAction(def, first));
                first = false;
            }
        }
        if (!result.isEmpty()) {
            log.debug("[ActionBuilder] pocId={} detected actions: {}",
                    pocId, result.stream().map(PocAction::getKey).toList());
        }
        return result;
    }

    private List<PocAction> fallback() {
        return List.of(toAction(StandardActions.ALL.get(0), true));
    }

    private PocAction toAction(StandardActions.ActionDef def, boolean isDefault) {
        return PocAction.builder()
                .key(def.key())
                .label(def.label())
                .category(def.category())
                .outputType(def.outputType())
                .riskLevel(def.riskLevel())
                .defaultAction(isDefault)
                .params(def.params())
                .build();
    }
}
