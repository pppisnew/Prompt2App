package com.prompt2app.eval;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 评测编排器（Phase 0 起步，Phase 5 升级到三维评分）。
 *
 * <p>两种入口：
 * <ul>
 *   <li>{@link #run(Path, Path)} —— Phase 0 stub baseline 模式（向后兼容，不调 LLM）</li>
 *   <li>{@link #runFull(Path, Path, Path, List)} —— Phase 5 三维评分 + diff vs prev baseline</li>
 * </ul>
 */
public class EvalRunner {

    private final EvalCaseLoader loader;
    private final AgentInvoker invoker;
    private final RubricScorer rubricScorer;
    private final MarkdownReporter reporter;

    public EvalRunner(AgentInvoker invoker) {
        this.loader = new EvalCaseLoader();
        this.invoker = invoker;
        this.rubricScorer = new RubricScorer();
        this.reporter = new MarkdownReporter();
    }

    /** Phase 0 模式：单维度（rubric）+ stub baseline。 */
    public void run(Path casesDir, Path reportFile) {
        List<EvalCase> cases = loader.loadAll(casesDir);
        if (cases.isEmpty()) {
            throw new IllegalStateException("No cases found in " + casesDir);
        }
        List<MarkdownReporter.CaseRun> runs = new ArrayList<>(cases.size());
        for (EvalCase ec : cases) {
            AgentInvoker.InvocationResult inv = invoker.invoke(ec);
            RubricScorer.CaseScore sc = rubricScorer.score(ec, inv);
            runs.add(new MarkdownReporter.CaseRun(ec, inv, sc));
        }
        reporter.writeBaseline(runs, invoker, reportFile);
    }

    /**
     * Phase 5 模式：多维度 Scorer 组合 + DiffReporter。
     *
     * @param casesDir   case 目录
     * @param reportFile 当次报表输出路径
     * @param prevReport 上一次 baseline 路径（可为 null/不存在 → 视为首次跑）
     * @param scorers    要使用的 Scorer 列表（顺序决定报表列顺序）
     */
    public void runFull(Path casesDir, Path reportFile, Path prevReport, List<Scorer> scorers) {
        List<EvalCase> cases = loader.loadAll(casesDir);
        if (cases.isEmpty()) {
            throw new IllegalStateException("No cases found in " + casesDir);
        }
        CompositeScorer composite = new CompositeScorer(scorers);

        List<MarkdownReporter.CaseFullRun> runs = new ArrayList<>(cases.size());
        List<CompositeScorer.CaseFinalScore> finalScores = new ArrayList<>(cases.size());
        for (EvalCase ec : cases) {
            AgentInvoker.InvocationResult inv = invoker.invoke(ec);
            CompositeScorer.CaseFinalScore fs = composite.score(ec, inv);
            runs.add(new MarkdownReporter.CaseFullRun(ec, inv, fs));
            finalScores.add(fs);
        }

        DiffReporter.DiffReport diff = new DiffReporter().diff(finalScores, prevReport);
        reporter.writeFull(runs, invoker, diff, reportFile);
    }

    /**
     * Stand-alone entrypoint:
     * <pre>
     *   mvn exec:java -Dexec.mainClass=com.prompt2app.eval.EvalRunner
     * </pre>
     */
    public static void main(String[] args) {
        Path casesDir = Paths.get(args.length > 0 ? args[0] : "eval/cases");
        Path reportFile = Paths.get(args.length > 1 ? args[1] : "eval/reports/baseline.md");
        new EvalRunner(new AgentInvoker.Stub()).run(casesDir, reportFile);
        System.out.println("Baseline written to " + reportFile.toAbsolutePath());
    }
}
