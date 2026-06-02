package com.camera.app.poc.service;

import com.camera.app.poc.dto.ParamField;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统内 6 个核心动作的规范定义。前后端统一以此为唯一事实来源。
 *
 * <p>每个 ActionDef 包含：
 * <ul>
 *   <li>key         — 唯一标识，如 CHECK_VULN</li>
 *   <li>label       — 前端展示名称</li>
 *   <li>category    — 分组：VERIFY / READ / DOWNLOAD / INTERACT</li>
 *   <li>outputType  — 结果渲染类型：BOOLEAN_TEXT / TEXT / JSON / IMAGE / FILE</li>
 *   <li>riskLevel   — 风险级别：LOW / MEDIUM / HIGH</li>
 *   <li>cliFlag     — 对应 Python 脚本 CLI flag，如 --check</li>
 *   <li>supportsOutputFile — 是否支持 --output 参数（FETCH_SNAPSHOT / DOWNLOAD_CONFIG）</li>
 *   <li>params      — 该动作专属参数定义（供前端渲染表单）</li>
 * </ul>
 */
public final class StandardActions {

    private StandardActions() {}

    public record ActionDef(
            String key,
            String label,
            String category,
            String outputType,
            String riskLevel,
            String cliFlag,
            boolean supportsOutputFile,
            List<ParamField> params
    ) {}

    // ─── 6 canonical actions ──────────────────────────────────────────────────

    private static final ParamField CMD_PARAM = ParamField.builder()
            .name("cmd").label("命令").type("text")
            .required(true).placeholder("请输入命令，如 whoami").build();

    private static final ParamField OUTPUT_FILE_PARAM = ParamField.builder()
            .name("outputFile").label("输出文件名").type("text")
            .required(false).placeholder("默认自动生成（如 snapshot.jpg）").build();

    static final ActionDef CHECK_VULN = new ActionDef(
            "CHECK_VULN", "漏洞检测", "VERIFY", "BOOLEAN_TEXT", "LOW",
            "--check", false, List.of());

    static final ActionDef EXEC_COMMAND = new ActionDef(
            "EXEC_COMMAND", "执行命令", "INTERACT", "TEXT", "HIGH",
            "--cmd", false, List.of(CMD_PARAM));

    static final ActionDef FETCH_SNAPSHOT = new ActionDef(
            "FETCH_SNAPSHOT", "获取快照", "READ", "IMAGE", "LOW",
            "--snapshot", true, List.of(OUTPUT_FILE_PARAM));

    static final ActionDef LIST_USERS = new ActionDef(
            "LIST_USERS", "读取用户列表", "READ", "JSON", "LOW",
            "--users", false, List.of());

    static final ActionDef DOWNLOAD_CONFIG = new ActionDef(
            "DOWNLOAD_CONFIG", "下载配置", "DOWNLOAD", "FILE", "LOW",
            "--config", true, List.of(OUTPUT_FILE_PARAM));

    static final ActionDef DUMP_CREDS = new ActionDef(
            "DUMP_CREDS", "读取管理员凭证", "READ", "JSON", "MEDIUM",
            "--dump-creds", false, List.of());

    /** Ordered list — first element is the default when no flags detected. */
    public static final List<ActionDef> ALL = List.of(
            CHECK_VULN, EXEC_COMMAND, FETCH_SNAPSHOT, LIST_USERS, DOWNLOAD_CONFIG, DUMP_CREDS);

    private static final Map<String, ActionDef> BY_KEY =
            ALL.stream().collect(Collectors.toMap(ActionDef::key, Function.identity()));

    // ─── lookup helpers ───────────────────────────────────────────────────────

    public static Optional<ActionDef> find(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Returns outputType for the given actionKey, or "TEXT" if unknown. */
    public static String outputType(String key) {
        return find(key).map(ActionDef::outputType).orElse("TEXT");
    }

    /** Returns label for the given actionKey, or the key itself if unknown. */
    public static String label(String key) {
        return find(key).map(ActionDef::label).orElse(key);
    }

    /** Returns the CLI flag (e.g. "--check") for the given actionKey, or "--check" if unknown. */
    public static String cliFlag(String key) {
        return find(key).map(ActionDef::cliFlag).orElse("--check");
    }

    /** Returns true if this action writes an output file (IMAGE/FILE artifacts). */
    public static boolean supportsOutputFile(String key) {
        return find(key).map(ActionDef::supportsOutputFile).orElse(false);
    }

    /** Returns true if this action can produce a file artifact (outputType IMAGE or FILE). */
    public static boolean producesArtifact(String key) {
        String ot = outputType(key);
        return "IMAGE".equals(ot) || "FILE".equals(ot);
    }
}
