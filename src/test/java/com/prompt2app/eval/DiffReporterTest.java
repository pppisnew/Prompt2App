package com.prompt2app.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DiffReporter} 单测：解析、diff 计算、回归判定、render Markdown。
 */
class DiffReporterTest {

    @TempDir
    Path tempRoot;

    private final DiffReporter diff = new DiffReporter();

    @Test
    void no_prev_baseline_yields_all_new() {
        List<CompositeScorer.CaseFinalScore> current = List.of(
                finalScore("003-todo-app-vue", 75.0, false),
                finalScore("004-saas-landing-hero", 60.0, false)
        );
        DiffReporter.DiffReport report = diff.diff(current, null);
        assertEquals(2, report.getRows().size());
        assertEquals("new", report.getRows().get(0).getStatus());
        assertNull(report.getPrevAvg());
        assertFalse(report.isOverallRegressed());
    }

    @Test
    void detects_per_case_regression_above_threshold() throws IOException {
        Path prev = writePrev(Map.of(
                "003-todo-app-vue", 80.0,
                "004-saas-landing-hero", 60.0
        ));
        List<CompositeScorer.CaseFinalScore> current = List.of(
                finalScore("003-todo-app-vue", 65.0, false),    // -15 → regressed
                finalScore("004-saas-landing-hero", 60.0, false) // 0 → stable
        );
        DiffReporter.DiffReport report = diff.diff(current, prev);
        assertEquals(1, report.getRegressedCaseCount());
        assertEquals("regressed", findRow(report, "003-todo-app-vue").getStatus());
        assertEquals("stable", findRow(report, "004-saas-landing-hero").getStatus());
    }

    @Test
    void detects_per_case_improvement_above_threshold() throws IOException {
        Path prev = writePrev(Map.of("003-todo-app-vue", 60.0));
        List<CompositeScorer.CaseFinalScore> current = List.of(
                finalScore("003-todo-app-vue", 75.0, false)  // +15 → improved
        );
        DiffReporter.DiffReport report = diff.diff(current, prev);
        assertEquals("improved", report.getRows().get(0).getStatus());
    }

    @Test
    void overall_regression_when_avg_drops_more_than_5() throws IOException {
        Path prev = writePrev(Map.of(
                "001-a", 80.0, "002-b", 80.0, "003-c", 80.0
        ));
        List<CompositeScorer.CaseFinalScore> current = List.of(
                finalScore("001-a", 70.0, false),
                finalScore("002-b", 70.0, false),
                finalScore("003-c", 70.0, false)
        );
        DiffReporter.DiffReport report = diff.diff(current, prev);
        assertEquals(80.0, report.getPrevAvg(), 0.0001);
        assertEquals(70.0, report.getCurrentAvg(), 0.0001);
        assertEquals(-10.0, report.getAvgDelta(), 0.0001);
        assertTrue(report.isOverallRegressed());
    }

    @Test
    void no_overall_regression_when_avg_drops_within_threshold() throws IOException {
        Path prev = writePrev(Map.of("001-a", 80.0));
        List<CompositeScorer.CaseFinalScore> current = List.of(
                finalScore("001-a", 77.0, false) // -3, within 5
        );
        DiffReporter.DiffReport report = diff.diff(current, prev);
        assertFalse(report.isOverallRegressed());
    }

    @Test
    void render_markdown_contains_diff_table_and_status_icons() throws IOException {
        Path prev = writePrev(Map.of("003-todo-app-vue", 80.0));
        List<CompositeScorer.CaseFinalScore> current = List.of(
                finalScore("003-todo-app-vue", 65.0, false)
        );
        DiffReporter.DiffReport report = diff.diff(current, prev);
        String md = diff.render(report);
        assertTrue(md.contains("Regression Diff"));
        assertTrue(md.contains("003-todo-app-vue"));
        assertTrue(md.contains("regressed"));
    }

    @Test
    void load_prev_from_real_baseline_format() throws IOException {
        // 模拟真实 baseline.md 中 Per-Case Detail 表格的行
        String baseline = "# Eval Report\n\n## Distribution\n| s | s | 1 |\n\n"
                + "## Per-Case Detail\n\n"
                + "| ID | Strategy | Rubric | Render | Judge | Final |\n"
                + "| --- | --- | --- | --- | --- | --- |\n"
                + "| 003-todo-app-vue | VUE_PROJECT | 90 | 100 | 75 | 75.0 |\n"
                + "| 004-saas-landing-hero | HTML | 80 | 100 | 60 | 60.0 |\n";
        Path prev = tempRoot.resolve("prev.md");
        Files.writeString(prev, baseline);

        Map<String, Double> map = DiffReporter.loadPrev(prev);
        assertEquals(2, map.size());
        assertEquals(75.0, map.get("003-todo-app-vue"));
        assertEquals(60.0, map.get("004-saas-landing-hero"));
    }

    @Test
    void load_prev_returns_empty_when_file_missing() {
        Map<String, Double> map = DiffReporter.loadPrev(tempRoot.resolve("does-not-exist.md"));
        assertNotNull(map);
        assertEquals(0, map.size());
    }

    // ---- helpers ----

    private CompositeScorer.CaseFinalScore finalScore(String id, double score, boolean veto) {
        return CompositeScorer.CaseFinalScore.builder()
                .caseId(id)
                .finalScore(score)
                .veto(veto)
                .contributions(List.of())
                .summary("test")
                .build();
    }

    private Path writePrev(Map<String, Double> idToScore) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Eval Report\n\n## Per-Case Detail\n\n");
        sb.append("| ID | Strategy | Rubric | Render | Judge | Final |\n");
        sb.append("| --- | --- | --- | --- | --- | --- |\n");
        idToScore.forEach((id, score) ->
                sb.append("| ").append(id).append(" | HTML | 90 | 100 | ")
                        .append(score).append(" | ").append(score).append(" |\n"));
        Path p = tempRoot.resolve("prev-" + System.nanoTime() + ".md");
        Files.writeString(p, sb.toString());
        return p;
    }

    private DiffReporter.CaseDiff findRow(DiffReporter.DiffReport report, String id) {
        return report.getRows().stream()
                .filter(r -> r.getCaseId().equals(id))
                .findFirst().orElseThrow();
    }
}
