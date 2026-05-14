package com.camera.app.poc.service;

import com.camera.app.poc.entity.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PocArgAssemblerTest {

    // ─── 1. CHECK + URL target ────────────────────────────────────────────────

    @Test
    void checkModeWithUrlTarget_injectsUrlFlagAndCheckFlag() {
        List<String> args = PocArgAssembler.assemble(
                ExecutionMode.CHECK, "http://1.2.3.4:8080", true, null);

        assertThat(args).containsExactly("-u", "http://1.2.3.4:8080", "--check");
    }

    // ─── 2. EXPLOIT + URL target ──────────────────────────────────────────────

    @Test
    void exploitModeWithUrlTarget_injectsUrlFlagButNoCheckFlag() {
        List<String> args = PocArgAssembler.assemble(
                ExecutionMode.EXPLOIT, "http://1.2.3.4:8080", true, null);

        assertThat(args).containsExactly("-u", "http://1.2.3.4:8080");
        assertThat(args).doesNotContain("--check");
    }

    // ─── 3. CHECK + bare IP (positional mode) ─────────────────────────────────

    @Test
    void checkModeWithBareIp_injectsPositionalArgAndCheckFlag() {
        List<String> args = PocArgAssembler.assemble(
                ExecutionMode.CHECK, "192.168.1.1", false, null);

        assertThat(args).containsExactly("192.168.1.1", "--check");
        assertThat(args).doesNotContain("-u");
    }

    // ─── 4. null mode + no target + compat args ───────────────────────────────

    @Test
    void nullModeNoTarget_onlyCompatArgs() {
        List<String> args = PocArgAssembler.assemble(
                null, null, false, List.of("--verbose", "--timeout=5"));

        assertThat(args).containsExactly("--verbose", "--timeout=5");
        assertThat(args).doesNotContain("--check");
    }

    // ─── 5. null byte in compat arg is filtered ───────────────────────────────

    @Test
    void nullByteInCompatArg_isFiltered() {
        List<String> args = PocArgAssembler.assemble(
                null, null, false, List.of("safe-arg", "bad\0arg", "another-safe"));

        assertThat(args).containsExactly("safe-arg", "another-safe");
        assertThat(args).noneMatch(a -> a.contains("\0"));
    }

    // ─── 6. CHECK + no asset → only --check ──────────────────────────────────

    @Test
    void checkModeNoTarget_onlyCheckFlag() {
        List<String> args = PocArgAssembler.assemble(
                ExecutionMode.CHECK, null, false, null);

        assertThat(args).containsExactly("--check");
    }

    // ─── 7. URL target includes explicit port ─────────────────────────────────

    @Test
    void explicitPortInUrl_targetContainsPort() {
        List<String> args = PocArgAssembler.assemble(
                ExecutionMode.CHECK, "http://10.0.0.1:8080", true, null);

        assertThat(args).contains("http://10.0.0.1:8080");
        String target = args.stream().filter(a -> a.startsWith("http://")).findFirst().orElse("");
        assertThat(target).contains(":8080");
    }
}
