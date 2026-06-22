package com.prompt2app.eval;

import com.prompt2app.infra.config.Prompt2AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MultiRoundEvalRunner} 编排 + 断点续跑测试。
 *
 * <p>用一个可控 invoker（返回固定 mergedOutput，记录调用次数）验证：
 * <ul>
 *   <li>3 轮跑通：JSON + MD 各 3 个，聚合结果 numRounds=3</li>
 *   <li>断点续跑：删 round-2 后重跑，仅 round-2 重新调 invoker</li>
 *   <li>resume=false：所有轮重新调 invoker</li>
 * </ul>
 */
class MultiRoundEvalRunnerTest {

    /** Test invoker：返回固定结果，记录每次调用。 */
    static class CountingInvoker implements AgentInvoker {
        final AtomicInteger invocationCount = new AtomicInteger(0);
        @Override public String name() { return "counting"; }
        @Override public InvocationResult invoke(EvalCase evalCase) {
            invocationCount.incrementAndGet();
            return InvocationResult.builder()
                    .mergedOutput("<body>" + "x".repeat(200) + "</body>")  // pass HTML render
                    .fileCount(1).durationMs(10L).invoked(true).buildSuccess(false)
                    .note("test").build();
        }
    }

    private Prompt2AppProperties.Eval cfg(Path tmp, int rounds, boolean resume) {
        Prompt2AppProperties.Eval e = new Prompt2AppProperties.Eval();
        e.setRounds(rounds);
        e.setResumeOnRestart(resume);
        e.setRoundReportsDir(tmp.toString());
        return e;
    }

    private Path createMiniCasesDir(@TempDir Path tmp) throws Exception {
        // Minimal eval case yaml so EvalCaseLoader returns 1 case
        Path casesDir = tmp.resolve("cases");
        Files.createDirectories(casesDir);
        String yaml = """
                id: t1-test-case
                title: Test
                difficulty: easy
                expected_strategy: HTML
                prompt: hello
                rubric:
                  must_contain: []
                  must_not_contain: []
                  min_files: 1
                """;
        Files.writeString(casesDir.resolve("t1.yaml"), yaml);
        return casesDir;
    }

    @Test
    void threeRounds_writesArtifactsAndAggregates(@TempDir Path tmp) throws Exception {
        Path casesDir = createMiniCasesDir(tmp);
        Path reportsDir = tmp.resolve("reports");
        Files.createDirectories(reportsDir);

        CountingInvoker invoker = new CountingInvoker();
        var runner = new MultiRoundEvalRunner(invoker,
                List.of(new RubricScorer(), new RenderScorer()),
                cfg(reportsDir, 3, true));

        var report = runner.runMultiRound(casesDir);

        // 3 rounds × 1 case = 3 invocations
        assertEquals(3, invoker.invocationCount.get());
        // 3 JSON + 3 MD
        for (int r = 1; r <= 3; r++) {
            assertTrue(Files.exists(reportsDir.resolve("baseline-real.round-" + r + ".json")));
            assertTrue(Files.exists(reportsDir.resolve("baseline-real.round-" + r + ".md")));
        }
        assertEquals(3, report.getNumRounds());
        assertEquals(1, report.getCases().size());
    }

    @Test
    void resume_skipsExistingRounds(@TempDir Path tmp) throws Exception {
        Path casesDir = createMiniCasesDir(tmp);
        Path reportsDir = tmp.resolve("reports");
        Files.createDirectories(reportsDir);

        // First run: 3 rounds (3 invocations)
        CountingInvoker inv1 = new CountingInvoker();
        new MultiRoundEvalRunner(inv1,
                List.of(new RubricScorer(), new RenderScorer()),
                cfg(reportsDir, 3, true))
                .runMultiRound(casesDir);
        assertEquals(3, inv1.invocationCount.get());

        // Delete round-2 JSON, keep round-1 & round-3
        Files.delete(reportsDir.resolve("baseline-real.round-2.json"));

        // Re-run: should only re-invoke for round-2 (round-1 & 3 load from JSON)
        CountingInvoker inv2 = new CountingInvoker();
        var report = new MultiRoundEvalRunner(inv2,
                List.of(new RubricScorer(), new RenderScorer()),
                cfg(reportsDir, 3, true))
                .runMultiRound(casesDir);

        assertEquals(1, inv2.invocationCount.get(), "Only round-2 should re-invoke (resume)");
        assertEquals(3, report.getNumRounds());
        // round-2 JSON regenerated
        assertTrue(Files.exists(reportsDir.resolve("baseline-real.round-2.json")));
    }

    @Test
    void resumeFalse_alwaysReinvokes(@TempDir Path tmp) throws Exception {
        Path casesDir = createMiniCasesDir(tmp);
        Path reportsDir = tmp.resolve("reports");
        Files.createDirectories(reportsDir);

        // Pre-populate all 3 round JSONs
        new MultiRoundEvalRunner(new CountingInvoker(),
                List.of(new RubricScorer(), new RenderScorer()),
                cfg(reportsDir, 3, true))
                .runMultiRound(casesDir);

        // Now re-run with resume=false: should invoke all 3
        CountingInvoker inv = new CountingInvoker();
        new MultiRoundEvalRunner(inv,
                List.of(new RubricScorer(), new RenderScorer()),
                cfg(reportsDir, 3, false))
                .runMultiRound(casesDir);
        assertEquals(3, inv.invocationCount.get());
    }
}
