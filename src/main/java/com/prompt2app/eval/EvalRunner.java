package com.prompt2app.eval;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 0 evaluation orchestrator.
 *
 * <p>Pipeline: {@link EvalCaseLoader load cases} → {@link AgentInvoker invoke agent}
 * → {@link RubricScorer score} → {@link MarkdownReporter write report}.
 *
 * <p>Phase 5 will wrap this with CI integration and diff-against-previous-baseline
 * regression checks.
 */
public class EvalRunner {

    private final EvalCaseLoader loader;
    private final AgentInvoker invoker;
    private final RubricScorer scorer;
    private final MarkdownReporter reporter;

    public EvalRunner(AgentInvoker invoker) {
        this.loader = new EvalCaseLoader();
        this.invoker = invoker;
        this.scorer = new RubricScorer();
        this.reporter = new MarkdownReporter();
    }

    /** Run all cases under {@code casesDir} and write a single Markdown report to {@code reportFile}. */
    public void run(Path casesDir, Path reportFile) {
        List<EvalCase> cases = loader.loadAll(casesDir);
        if (cases.isEmpty()) {
            throw new IllegalStateException("No cases found in " + casesDir);
        }

        List<MarkdownReporter.CaseRun> runs = new ArrayList<>(cases.size());
        for (EvalCase ec : cases) {
            AgentInvoker.InvocationResult inv = invoker.invoke(ec);
            RubricScorer.CaseScore sc = scorer.score(ec, inv);
            runs.add(new MarkdownReporter.CaseRun(ec, inv, sc));
        }
        reporter.writeBaseline(runs, invoker, reportFile);
    }

    /**
     * Stand-alone entrypoint:
     * <pre>
     *   mvn exec:java -Dexec.mainClass=com.prompt2app.eval.EvalRunner
     * </pre>
     * Defaults to {@code eval/cases} → {@code eval/reports/baseline.md}.
     */
    public static void main(String[] args) {
        Path casesDir = Paths.get(args.length > 0 ? args[0] : "eval/cases");
        Path reportFile = Paths.get(args.length > 1 ? args[1] : "eval/reports/baseline.md");
        new EvalRunner(new AgentInvoker.Stub()).run(casesDir, reportFile);
        System.out.println("Baseline written to " + reportFile.toAbsolutePath());
    }
}
