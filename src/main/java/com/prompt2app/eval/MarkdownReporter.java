package com.prompt2app.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes baseline / phase reports to {@code eval/reports/}.
 *
 * <p>Phase 0 format is intentionally minimal: distribution, summary, per-case table.
 * Phase 5 will add diff-against-prev-baseline, regression flags, and LLM-Judge breakdowns.
 */
public class MarkdownReporter {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void writeBaseline(List<CaseRun> runs, AgentInvoker invoker, Path output) {
        StringBuilder sb = new StringBuilder(8 * 1024);
        appendHeader(sb, runs, invoker);
        appendDistribution(sb, runs);
        appendSummary(sb, runs);
        appendPerCase(sb, runs);

        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, sb.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write baseline report: " + output, e);
        }
    }

    private void appendHeader(StringBuilder sb, List<CaseRun> runs, AgentInvoker invoker) {
        sb.append("# Baseline Report\n\n");
        sb.append("- **Generated**: ").append(LocalDateTime.now().format(ISO)).append("\n");
        sb.append("- **Invoker**: `").append(invoker.name()).append("`\n");
        sb.append("- **Cases**: ").append(runs.size()).append("\n\n");

        if ("stub".equals(invoker.name())) {
            sb.append("> ⚠️ **Stub mode** — no real LLM calls were made. ");
            sb.append("This file shows the eval set's structural overview only. ");
            sb.append("Real baseline scores will land once a non-stub `AgentInvoker` is wired ");
            sb.append("(planned for Phase 1+ after the codebase compiles cleanly).\n\n");
        }
    }

    private void appendDistribution(StringBuilder sb, List<CaseRun> runs) {
        Map<String, Long> byStrategy = new TreeMap<>();
        Map<String, Long> byDifficulty = new TreeMap<>();
        for (CaseRun run : runs) {
            byStrategy.merge(run.evalCase().getExpectedStrategy(), 1L, Long::sum);
            byDifficulty.merge(run.evalCase().getDifficulty(), 1L, Long::sum);
        }
        sb.append("## Distribution\n\n");
        sb.append("| Dimension | Bucket | Count |\n| --- | --- | --- |\n");
        byStrategy.forEach((k, v) -> sb.append("| Strategy | ").append(k).append(" | ").append(v).append(" |\n"));
        byDifficulty.forEach((k, v) -> sb.append("| Difficulty | ").append(k).append(" | ").append(v).append(" |\n"));
        sb.append("\n");
    }

    private void appendSummary(StringBuilder sb, List<CaseRun> runs) {
        long invoked = runs.stream().filter(r -> r.score().isInvoked()).count();
        double avgScore = runs.stream()
                .filter(r -> r.score().isInvoked())
                .mapToDouble(r -> r.score().getDeterministicScore())
                .average().orElse(0.0);
        sb.append("## Summary\n\n");
        sb.append("- Invoked: ").append(invoked).append(" / ").append(runs.size()).append("\n");
        sb.append("- Avg deterministic score (invoked only): ")
                .append(String.format(Locale.ROOT, "%.2f", avgScore)).append(" / 100\n\n");
    }

    private void appendPerCase(StringBuilder sb, List<CaseRun> runs) {
        sb.append("## Per-Case Detail\n\n");
        sb.append("| ID | Title | Difficulty | Strategy | Invoked | mustContain | mustNotContain | minFiles | Score |\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (CaseRun r : runs) {
            EvalCase c = r.evalCase();
            RubricScorer.CaseScore s = r.score();
            sb.append("| ").append(c.getId());
            sb.append(" | ").append(c.getTitle());
            sb.append(" | ").append(c.getDifficulty());
            sb.append(" | ").append(c.getExpectedStrategy());
            sb.append(" | ").append(s.isInvoked() ? "✅" : "—");
            sb.append(" | ").append(s.getMustContainHits()).append("/").append(s.getMustContainTotal());
            sb.append(" | ").append(s.getMustNotContainHits()).append("/").append(s.getMustNotContainTotal());
            sb.append(" | ");
            Integer minFiles = c.getRubric() == null ? null : c.getRubric().getMinFiles();
            if (minFiles == null) {
                sb.append("—");
            } else {
                sb.append(s.isMinFilesOk() ? "✅" : "❌");
            }
            sb.append(" | ");
            sb.append(s.isInvoked() ? String.format(Locale.ROOT, "%.1f", s.getDeterministicScore()) : "—");
            sb.append(" |\n");
        }
        sb.append("\n");
    }

    /** Aggregate of a single case's invocation + score. Plain record. */
    public record CaseRun(EvalCase evalCase, AgentInvoker.InvocationResult invocation, RubricScorer.CaseScore score) {
    }
}
