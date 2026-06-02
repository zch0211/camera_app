package com.camera.app.poc.service;

import com.camera.app.poc.dto.ParamField;
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
 * 识别规则（按优先级从前到后扫描脚本 flags）：
 *   --check    → CHECK_VULN     (VERIFY,   BOOLEAN_TEXT, LOW)
 *   --cmd      → EXEC_COMMAND   (INTERACT, TEXT,         HIGH)
 *   --snapshot → FETCH_SNAPSHOT (READ,     IMAGE,        LOW)
 *   --users    → LIST_USERS     (READ,     JSON,         LOW)
 *   --config   → DOWNLOAD_CONFIG(DOWNLOAD, FILE,         LOW)
 *
 * 识别不到任何 flag → 回退到仅含 CHECK_VULN 的默认动作列表。
 * 第一个被识别到的动作自动标记为 defaultAction=true。
 */
@Slf4j
@Component
public class PocActionSchemaBuilder {

    private record ActionRule(
            String flag,
            String key,
            String label,
            String category,
            String outputType,
            String riskLevel,
            List<ParamField> params
    ) {}

    private static final List<ActionRule> PYTHON_RULES = List.of(
            new ActionRule("--check",
                    "CHECK_VULN", "漏洞检测", "VERIFY", "BOOLEAN_TEXT", "LOW",
                    List.of()),
            new ActionRule("--cmd",
                    "EXEC_COMMAND", "执行命令", "INTERACT", "TEXT", "HIGH",
                    List.of(ParamField.builder()
                            .name("cmd").label("命令").type("text")
                            .required(true).placeholder("请输入命令，如 whoami").build())),
            new ActionRule("--snapshot",
                    "FETCH_SNAPSHOT", "获取快照", "READ", "IMAGE", "LOW",
                    List.of()),
            new ActionRule("--users",
                    "LIST_USERS", "读取用户列表", "READ", "JSON", "LOW",
                    List.of()),
            new ActionRule("--config",
                    "DOWNLOAD_CONFIG", "下载配置", "DOWNLOAD", "FILE", "LOW",
                    List.of())
    );

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

    /** 根据 action key 返回其预期 outputType 字符串，未知 key 返回 "TEXT"。 */
    public String getOutputType(String actionKey) {
        return PYTHON_RULES.stream()
                .filter(r -> r.key().equals(actionKey))
                .map(ActionRule::outputType)
                .findFirst()
                .orElse("TEXT");
    }

    /** 根据 action key 返回其显示 label，未知 key 直接返回 key 本身。 */
    public String getLabel(String actionKey) {
        return PYTHON_RULES.stream()
                .filter(r -> r.key().equals(actionKey))
                .map(ActionRule::label)
                .findFirst()
                .orElse(actionKey);
    }

    // ─── private ──────────────────────────────────────────────────────────────

    private List<PocAction> detectFromContent(Long pocId, String content) {
        List<PocAction> result = new ArrayList<>();
        boolean first = true;
        for (ActionRule rule : PYTHON_RULES) {
            if (content.contains(rule.flag())) {
                result.add(toAction(rule, first));
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
        return List.of(toAction(PYTHON_RULES.get(0), true));
    }

    private PocAction toAction(ActionRule rule, boolean isDefault) {
        return PocAction.builder()
                .key(rule.key())
                .label(rule.label())
                .category(rule.category())
                .outputType(rule.outputType())
                .riskLevel(rule.riskLevel())
                .defaultAction(isDefault)
                .params(rule.params())
                .build();
    }
}
