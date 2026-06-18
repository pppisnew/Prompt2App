package com.prompt2app.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Smoke test: end-to-end pipeline with the {@link AgentInvoker.Stub stub invoker}.
 *
 * <p>Side effect: writes {@code eval/reports/baseline.md} as the build artifact.
 * That file becomes the "structural baseline" — committed manually if it looks right.
 *
 * <p>Real LLM-based baseline lands once a non-stub invoker exists (Phase 1+).
 *
 * <p>No Spring context, no LLM call, no temp dirs — fully deterministic and fast.
 */
class EvalRunnerSmokeTest {

    @Test
    void runs_full_pipeline_with_stub_and_writes_baseline() throws Exception {
        Path casesDir = Paths.get("eval/cases");
        Path reportFile = Paths.get("eval/reports/baseline.md");

        new EvalRunner(new AgentInvoker.Stub()).run(casesDir, reportFile);

        assertTrue(Files.exists(reportFile), "baseline.md should be written");

        String content = Files.readString(reportFile);
        // Header & mode markers
        assertTrue(content.contains("# Baseline Report"), "should have title");
        assertTrue(content.contains("Stub mode"),         "should mark stub mode prominently");
        // Distribution from 25 v0 cases
        assertTrue(content.contains("HTML"),        "should list HTML strategy");
        assertTrue(content.contains("MULTI_FILE"),  "should list MULTI_FILE strategy");
        assertTrue(content.contains("VUE_PROJECT"), "should list VUE_PROJECT strategy");
        // Per-case table presence
        assertTrue(content.contains("Per-Case Detail"), "should have per-case table");
        // Spot-check that several known case ids made it in
        assertTrue(content.contains("001-personal-resume-page"));
        assertTrue(content.contains("019-pomodoro-timer-vue"));
        assertTrue(content.contains("025-tic-tac-toe-vue"));

        // Sanity check on stub-mode invocation status: nothing should be marked invoked
        if (content.contains(" ✅ |")) {
            // ✅ may legitimately appear in the minFiles column when minFiles is null;
            // here we just assert it does NOT appear in the "Invoked" column position
            // (heuristic check via line scan).
            for (String line : content.split("\n")) {
                if (line.startsWith("| 001-")) {
                    String[] cols = line.split("\\|");
                    // cols layout: "", id, title, difficulty, strategy, invoked, ...
                    if (cols.length > 5 && cols[5].trim().equals("✅")) {
                        fail("Stub mode but case 001 reported as invoked: " + line);
                    }
                }
            }
        }
    }
}
