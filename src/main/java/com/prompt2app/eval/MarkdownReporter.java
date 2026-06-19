package com.prompt2app.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 评测报表写入器（Phase 5 升级）。
 *
 * <p>支持两种产出：
 * <ul>
 *   <li>{@link #writeBaseline} —— Phase 0 起就有的 stub 模式快照</li>
 *   <li>{@link #writeFull} —— Phase 5 新增：含三维评分 + diff 段</li>
 * </ul>
 */
public class MarkdownReporter {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------- Phase 0 baseline (stub 模式) ----------

    public void writeBaseline(List<CaseRun> runs, AgentInvoker invoker, Path output) {
        StringBuilder sb = new StringBuilder(8 * 1024);
        appendHeader(sb, runs, invoker);
        appendDistribution(sb, runs);
        appendSummary(sb, runs);
        appendPerCase(sb, runs);
        write(output, sb.toString());
    }

    // ---------- Phase 5 full report (3-dim + diff) ----------

    /** Phase 5 完整报表：含三维评分 + diff 段。 */
    public void writeFull(List<CaseFullRun> runs,
                          AgentInvoker invoker,
                          DiffReporter.DiffReport diff,
                          Path output) {
        StringBuilder sb = new StringBuilder(16 * 1024);
        appendFullHeader(sb, runs, invoker);
        appendFullDistribution(sb, runs);
        appendFullSummary(sb, runs);
        appendFullPerCase(sb, runs);
        if (diff != null) {
            sb.append(new DiffReporter().render(diff));
        }
        write(output, sb.toString());
    }

    // ---------- helpers ----------

    private void appendHeader(StringBuilder sb, List<CaseRun> runs, AgentInvoker invoker) {
        sb.append("# Baseline Report\n\n");
        sb.append("- **Generated**: ").append(LocalDateTime.now().format(ISO)).append("\n");
        sb.append("- **Invoker**: `").append(invoker.name()).append("`\n");
        sb.append("- **Cases**: ").append(runs.size()).append("\n\n");
        if ("stub".equals(invoker.name())) {
            sb.append("> ⚠️ **Stub mode** — no real LLM calls were made. ");
            sb.append("This file shows the eval set's structural overview only.\n\n");
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

    private void appendFullHeader(StringBuilder sb, List<CaseFullRun> runs, AgentInvoker invoker) {
        sb.append("# Eval Report (Phase 5 · 3-dim)\n\n");
        sb.append("- **Generated**: ").append(LocalDateTime.now().format(ISO)).append("\n");
        sb.append("- **Invoker**: `").append(invoker.name()).append("`\n");
        sb.append("- **Cases**: ").append(runs.size()).append("\n\n");
    }

    private void appendFullDistribution(StringBuilder sb, List<CaseFullRun> runs) {
        Map<String, Long> byStrategy = new TreeMap<>();
        for (CaseFullRun run : runs) {
            byStrategy.merge(run.evalCase().getExpectedStrategy(), 1L, Long::sum);
        }
        sb.append("## Distribution\n\n");
        sb.append("| Strategy | Count |\n| --- | --- |\n");
        byStrategy.forEach((k, v) -> sb.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        sb.append("\n");
    }

    private void appendFullSummary(StringBuilder sb, List<CaseFullRun> runs) {
        double avg = runs.stream().mapToDouble(r -> r.finalScore().getFinalScore()).average().orElse(0.0);
        long vetoed = runs.stream().filter(r -> r.finalScore().isVeto()).count();
        sb.append("## Summary\n\n");
        sb.append("- Avg final score: ").append(String.format(Locale.ROOT, "%.2f", avg)).append(" / 100\n");
        sb.append("- Vetoed (final 0): ").append(vetoed).append(" / ").append(runs.size()).append("\n\n");
    }

    private void appendFullPerCase(StringBuilder sb, List<CaseFullRun> runs) {
        sb.append("## Per-Case Detail\n\n");
        sb.append("| ID | Strategy | Rubric | Render | Judge | Final |\n");
        sb.append("| --- | --- | --- | --- | --- | --- |\n");
        for (CaseFullRun r : runs) {
            EvalCase c = r.evalCase();
            sb.append("| ").append(c.getId());
            sb.append(" | ").append(c.getExpectedStrategy());
            sb.append(" | ").append(formatContrib(r, "rubric"));
            sb.append(" | ").append(formatContrib(r, "render"));
            sb.append(" | ").append(formatContrib(r, "llm-judge"));
            sb.append(" | ").append(String.format(Locale.ROOT, "%.1f", r.finalScore().getFinalScore()));
            sb.append(" |\n");
        }
        sb.append("\n");
    }

    private static String formatContrib(CaseFullRun r, String dim) {
        for (Scorer.ScoreContribution c : r.finalScore().getContributions()) {
            if (dim.equals(c.getDimension())) {
                if (c.isVeto()) {
                    return "❌";
                }
                return String.format(Locale.ROOT, "%.0f", c.getScore());
            }
        }
        return "—";
    }

    private static void write(Path output, String content) {
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write report: " + output, e);
        }
    }

    /** Phase 0：单维度结果。 */
    public record CaseRun(EvalCase evalCase,
                          AgentInvoker.InvocationResult invocation,
                          RubricScorer.CaseScore score) {
    }

    /** Phase 5：三维结果。 */
    public record CaseFullRun(EvalCase evalCase,
                              AgentInvoker.InvocationResult invocation,
                              CompositeScorer.CaseFinalScore finalScore) {
    }
}
