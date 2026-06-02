package com.camera.app.poc.service;

import com.camera.app.poc.entity.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for structured-request → script argv mapping.
 *
 * v2 method: {@link #assembleForAction} — action key drives flag selection (preferred)
 * v1 method: {@link #assemble}          — mode enum drives flag selection (compat only)
 *
 * Action key → CLI flag mapping (authoritative):
 *   CHECK_VULN      → --check
 *   EXEC_COMMAND    → --cmd <params.cmd>
 *   FETCH_SNAPSHOT  → --snapshot [params.outputFile]
 *   LIST_USERS      → --users
 *   DOWNLOAD_CONFIG → --config [params.outputFile]
 *   DUMP_CREDS      → --dump-creds
 *   (unknown key)   → --check  (safe fallback)
 *
 * For FETCH_SNAPSHOT and DOWNLOAD_CONFIG the caller (PocExecutionServiceImpl) pre-fills
 * params.outputFile with an auto-generated filename before calling this assembler,
 * so the flag is always followed by a concrete filename.
 */
public final class PocArgAssembler {

    private PocArgAssembler() {}

    // ─── v2: action-key driven ────────────────────────────────────────────────

    /**
     * Assembles the argument list for action-driven execution (schemaVersion=2).
     *
     * @param actionKey   action key from PocExecuteRequest.actionKey
     * @param usedTarget  URL ({@code http://ip:port}) or bare IP, or null if no asset
     * @param urlMode     true  → inject as {@code -u <usedTarget>}
     *                    false → inject as positional bare IP
     * @param compatArgs  legacy argument list (appended last; null bytes filtered)
     * @param params      structured params map; EXEC_COMMAND reads {@code params.cmd};
     *                    FETCH_SNAPSHOT/DOWNLOAD_CONFIG read {@code params.outputFile}
     */
    public static List<String> assembleForAction(String actionKey,
                                                  String usedTarget,
                                                  boolean urlMode,
                                                  List<String> compatArgs,
                                                  Map<String, Object> params) {
        List<String> args = new ArrayList<>();

        // 1. Target injection
        injectTarget(args, usedTarget, urlMode);

        // 2. Action-specific flags
        switch (actionKey != null ? actionKey : "CHECK_VULN") {
            case "CHECK_VULN" -> args.add("--check");
            case "EXEC_COMMAND" -> {
                String cmd = extractString(params, "cmd");
                if (cmd != null && !cmd.isBlank()) {
                    args.add("--cmd");
                    args.add(cmd);
                }
            }
            case "FETCH_SNAPSHOT" -> {
                args.add("--snapshot");
                String of = extractString(params, "outputFile");
                if (of != null && !of.isBlank()) args.add(of);
            }
            case "LIST_USERS" -> args.add("--users");
            case "DOWNLOAD_CONFIG" -> {
                args.add("--config");
                String of = extractString(params, "outputFile");
                if (of != null && !of.isBlank()) args.add(of);
            }
            case "DUMP_CREDS" -> args.add("--dump-creds");
            default -> args.add("--check"); // unknown key → safe fallback
        }

        // 3. Legacy / extra arguments (appended last)
        appendCompatArgs(args, compatArgs);

        return args;
    }

    // ─── v1: mode-driven (kept for backward compat; tests rely on this) ───────

    /**
     * Assembles the argument list passed to the Python script (everything after
     * {@code python script.py}).
     *
     * @param mode        execution mode; null → no mode flag added
     * @param usedTarget  URL ({@code http://ip:port}) or bare IP, or null if no asset
     * @param urlMode     true  → inject as {@code -u <usedTarget>}
     *                    false → inject as positional bare IP
     * @param compatArgs  legacy argument list (appended last; null bytes filtered)
     * @param params      structured params map; EXPLOIT reads {@code params.cmd}
     */
    public static List<String> assemble(ExecutionMode mode,
                                        String usedTarget,
                                        boolean urlMode,
                                        List<String> compatArgs,
                                        Map<String, Object> params) {
        List<String> args = new ArrayList<>();

        // 1. Target injection
        injectTarget(args, usedTarget, urlMode);

        // 2. Mode-specific flags
        if (mode == ExecutionMode.CHECK) {
            args.add("--check");
        } else if (mode == ExecutionMode.EXPLOIT) {
            String cmd = extractString(params, "cmd");
            if (cmd != null && !cmd.isBlank()) {
                args.add("--cmd");
                args.add(cmd);
            }
        }

        // 3. Legacy / extra arguments (appended last)
        appendCompatArgs(args, compatArgs);

        return args;
    }

    // ─── shared helpers ───────────────────────────────────────────────────────

    private static void injectTarget(List<String> args, String usedTarget, boolean urlMode) {
        if (usedTarget != null) {
            if (urlMode) {
                args.add("-u");
                args.add(usedTarget);
            } else {
                args.add(usedTarget);
            }
        }
    }

    private static void appendCompatArgs(List<String> args, List<String> compatArgs) {
        if (compatArgs != null) {
            for (String arg : compatArgs) {
                if (arg != null && !arg.contains("\0")) {
                    args.add(arg);
                }
            }
        }
    }

    private static String extractString(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }
}
