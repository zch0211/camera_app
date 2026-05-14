package com.camera.app.poc.service;

import com.camera.app.poc.entity.ExecutionMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for structured-request → script argv mapping.
 *
 * Mode flag table:
 *   CHECK   → --check   (read-only probe)
 *   EXPLOIT → (no extra flag in this version)
 *   null    → (treated as legacy; no mode flag appended)
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
     */
    public static List<String> assemble(ExecutionMode mode,
                                        String usedTarget,
                                        boolean urlMode,
                                        List<String> compatArgs) {
        List<String> args = new ArrayList<>();

        // ── 1. Target injection ──────────────────────────────────────────────
        if (usedTarget != null) {
            if (urlMode) {
                args.add("-u");
                args.add(usedTarget);
            } else {
                // legacy positional-arg mode: bare IP as first arg
                args.add(usedTarget);
            }
        }

        // ── 2. Mode-specific flags ───────────────────────────────────────────
        if (mode == ExecutionMode.CHECK) {
            args.add("--check");
        }
        // EXPLOIT: no dedicated flag in this version

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
}
