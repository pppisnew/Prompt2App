package com.prompt2app.eval;

import com.prompt2app.infra.config.Prompt2AppProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮评测编排器（ADR-0013）。
 *
 * <p>责任：
 * <ol>
 *   <li>跑 N 轮（N 来自 {@link Prompt2AppProperties.Eval#getRounds()}）</li>
 *   <li>每轮独立 appId 区间（由 {@link DirectServiceInvoker#setRoundContext(int)} 隔离产物）</li>
 *   <li>每轮跑完双写：{@code .json}（机器读，断点续跑/聚合）+ {@code .md}（人读，单轮排查）</li>
 *   <li>断点续跑：{@code .json} 已存在则 load 跳过该轮</li>
 *   <li>聚合 N 轮 → {@link RoundAggregator.RoundedReport}</li>
 * </ol>
 *
 * <p>不动 {@link EvalRunner}（保留 stub smoke 用），独立编排。
 */
@Slf4j
public class MultiRoundEvalRunner {

    private final EvalCaseLoader loader;
    private final AgentInvoker invoker;
    private final List<Scorer> scorers;
    private final MarkdownReporter reporter;
    private final RoundAggregator aggregator;
    private final Prompt2AppProperties.Eval evalConfig;

    public MultiRoundEvalRunner(AgentInvoker invoker,
                                List<Scorer> scorers,
                                Prompt2AppProperties.Eval evalConfig) {
        this.loader = new EvalCaseLoader();
        this.invoker = invoker;
        this.scorers = scorers;
        this.reporter = new MarkdownReporter();
        this.aggregator = new RoundAggregator();
        this.evalConfig = evalConfig;
    }

    /**
     * 多轮跑评测，返回聚合结果。
     *
     * @param casesDir   case 目录
     * @param finalReport 最终聚合报告输出路径（多轮 markdown 报告 v2）
     * @return 聚合报告（含每 case 均分/标准差、维度均分、总均分）
     */
    public RoundAggregator.RoundedReport runMultiRound(Path casesDir, Path finalReport) {
        List<EvalCase> cases = loader.loadAll(casesDir);
        if (cases.isEmpty()) {
            throw new IllegalStateException("No cases found in " + casesDir);
        }
        int totalRounds = evalConfig.getRounds();
        if (totalRounds < 1) {
            throw new IllegalStateException("eval.rounds must be >= 1, got " + totalRounds);
        }
        log.info("[Eval] Starting multi-round run: {} cases × {} rounds (resume={})",
                cases.size(), totalRounds, evalConfig.isResumeOnRestart());

        List<RoundResult> rounds = new ArrayList<>();
        for (int r = 1; r <= totalRounds; r++) {
            Path jsonPath = resolveRoundJsonPath(r);
            Path mdPath = resolveRoundMdPath(r);

            if (evalConfig.isResumeOnRestart() && Files.exists(jsonPath)) {
                log.info("[Eval] Round {} 中间 JSON 已存在 ({}), 断点续跑跳过 LLM 调用", r, jsonPath);
                rounds.add(RoundResult.readJson(jsonPath));
                continue;
            }

            log.info("[Eval] Round {}/{} 开始", r, totalRounds);
            RoundResult result = runSingleRound(r, cases, mdPath);
            result.writeJson(jsonPath);
            log.info("[Eval] Round {} 完成: JSON→{}, MD→{}", r, jsonPath, mdPath);
            rounds.add(result);
        }

        RoundAggregator.RoundedReport report = aggregator.aggregate(rounds);
        log.info("[Eval] Multi-round done: totalMean={} ± {} (n={}, veto={})",
                report.getTotalMean(), report.getTotalStdev(),
                report.getNumRounds(), report.getVetoCount());
        if (finalReport != null) {
            reporter.writeMultiRound(report, invoker, finalReport);
            log.info("[Eval] Aggregated v2 report → {}", finalReport);
        }
        return report;
    }

    /** Convenience overload: no final report file (for tests / programmatic use). */
    public RoundAggregator.RoundedReport runMultiRound(Path casesDir) {
        return runMultiRound(casesDir, null);
    }

    /** Run one round: invoke + score + write single-round MD; return RoundResult for JSON write. */
    private RoundResult runSingleRound(int roundId, List<EvalCase> cases, Path mdPath) {
        // 切到本轮的 appId 区间（产物隔离）
        if (invoker instanceof DirectServiceInvoker dsi) {
            dsi.setRoundContext(roundId);
        }

        CompositeScorer composite = new CompositeScorer(scorers);
        List<MarkdownReporter.CaseFullRun> mdRuns = new ArrayList<>(cases.size());
        List<CompositeScorer.CaseFinalScore> finalScores = new ArrayList<>(cases.size());

        for (EvalCase ec : cases) {
            AgentInvoker.InvocationResult inv = invoker.invoke(ec, roundId);
            CompositeScorer.CaseFinalScore fs = composite.score(ec, inv);
            mdRuns.add(new MarkdownReporter.CaseFullRun(ec, inv, fs));
            finalScores.add(fs);
        }

        // 写单轮 Markdown 报告（不含 diff——单轮 vs 历史无意义，diff 在聚合层做）
        reporter.writeFull(mdRuns, invoker, null, mdPath);

        return RoundResult.fromFinalScores(roundId, invoker.name(), finalScores, cases);
    }

    private Path resolveRoundJsonPath(int roundId) {
        return Paths.get(evalConfig.getRoundReportsDir(),
                "baseline-real.round-" + roundId + ".json");
    }

    private Path resolveRoundMdPath(int roundId) {
        return Paths.get(evalConfig.getRoundReportsDir(),
                "baseline-real.round-" + roundId + ".md");
    }
}
