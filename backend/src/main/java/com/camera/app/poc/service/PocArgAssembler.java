package com.camera.app.poc.service;

import com.camera.app.poc.entity.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for structured-request → script argv mapping.
 *
 * Mode flag table:
 *   CHECK   → --check
 *   EXPLOIT → --cmd <params.cmd>   (service must validate cmd is present before calling)
 *   null    → (legacy; no mode flag appended)
 */
public final class PocArgAssembler {

    private PocArgAssembler() {}

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

        // ── 1. Target injection ──────────────────────────────────────────────
        if (usedTarget != null) {
            if (urlMode) {
                args.add("-u");
                args.add(usedTarget);
            } else {
                args.add(usedTarget);
            }
        }

        // ── 2. Mode-specific flags ───────────────────────────────────────────
        if (mode == ExecutionMode.CHECK) {
            args.add("--check");
        } else if (mode == ExecutionMode.EXPLOIT) {
            String cmd = extractString(params, "cmd");
            if (cmd != null && !cmd.isBlank()) {
                args.add("--cmd");
                args.add(cmd);
            }
        }

        // ── 3. Legacy / extra arguments (appended last) ──────────────────────
        if (compatArgs != null) {
            for (String arg : compatArgs) {
                if (arg != null && !arg.contains("\0")) {
                    args.add(arg);
                }
            }
        }

        return args;
    }

    private static String extractString(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }
}
