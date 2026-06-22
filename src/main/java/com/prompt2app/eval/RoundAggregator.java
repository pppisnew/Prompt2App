package com.prompt2app.eval;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多轮评测聚合器（ADR-0013）。
 *
 * <p>输入 N 个 {@link RoundResult}（每轮一个），输出 {@link RoundedReport}：
 * <ul>
 *   <li>每 case：N 轮分数 → 均值 / 标准差（总体标准差，样本视为全量）</li>
 *   <li>每维度：N 轮子均分 → 维度均值 / 标准差</li>
 *   <li>总分：N 轮总均分 → 总均分均值 / 标准差</li>
 * </ul>
 *
 * <p>数值精度：保留 2 位小数。标准差为 {@code 0} 时显示为 0.00。
 */
@Slf4j
public class RoundAggregator {

    /**
     * Aggregate N rounds into a single report.
     *
     * @param rounds N >= 1 (caller ensures). If N=1, stdev=0 for everything.
     */
    public RoundedReport aggregate(List<RoundResult> rounds) {
        if (rounds == null || rounds.isEmpty()) {
            throw new IllegalArgumentException("at least one round required");
        }
        int n = rounds.size();
        List<AggregatedCaseScore> aggregatedCases = new ArrayList<>();

        // Merge by caseId (all rounds should have identical case lists)
        Map<String, List<RoundResult.CaseRoundScore>> byCaseId = new LinkedHashMap<>();
        for (RoundResult r : rounds) {
            for (RoundResult.CaseRoundScore cs : r.getCases()) {
                byCaseId.computeIfAbsent(cs.getCaseId(), k -> new ArrayList<>()).add(cs);
            }
        }

        for (Map.Entry<String, List<RoundResult.CaseRoundScore>> entry : byCaseId.entrySet()) {
            String caseId = entry.getKey();
            List<RoundResult.CaseRoundScore> perRound = entry.getValue();
            if (perRound.size() != n) {
                log.warn("case {} has {} rounds, expected {} — some rounds may have failed for this case",
                        caseId, perRound.size(), n);
            }

            double[] scores = perRound.stream().mapToDouble(RoundResult.CaseRoundScore::getFinalScore).toArray();
            double mean = mean(scores);
            double stdev = stdev(scores, mean);

            // Strategy from the last round that reported it
            String strategy = perRound.get(perRound.size() - 1).getStrategy();

            // Per-dimension aggregation
            Map<String, DimAgg> perDimAgg = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                for (Map.Entry<String, Double> dim : perRound.get(i).getPerDim().entrySet()) {
                    double val = Math.max(0, dim.getValue());  // -1 (= veto) → 0 for aggregation
                    perDimAgg.computeIfAbsent(dim.getKey(), k -> new DimAgg())
                            .add(val);
                }
            }
            Map<String, double[]> perDimResult = new LinkedHashMap<>();
            for (Map.Entry<String, DimAgg> d : perDimAgg.entrySet()) {
                double[] vals = d.getValue().values();
                double dm = mean(vals);
                double ds = stdev(vals, dm);
                perDimResult.put(d.getKey(), new double[]{dm, ds});
            }

            aggregatedCases.add(AggregatedCaseScore.builder()
                    .caseId(caseId)
                    .strategy(strategy)
                    .scores(scores)
                    .mean(roundTo2(mean))
                    .stdev(roundTo2(stdev))
                    .perDim(perDimResult)
                    .build());
        }

        // Per-strategy sub-means
        Map<String, List<Double>> strategyGroups = new LinkedHashMap<>();
        for (AggregatedCaseScore cs : aggregatedCases) {
            strategyGroups.computeIfAbsent(cs.getStrategy(), k -> new ArrayList<>()).add(cs.getMean());
        }
        Map<String, double[]> strategyMeans = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> entry : strategyGroups.entrySet()) {
            double[] vals = entry.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            double sm = mean(vals);
            double ss = stdev(vals, sm);
            strategyMeans.put(entry.getKey(), new double[]{roundTo2(sm), roundTo2(ss)});
        }

        // Per-round totals (total mean per round)
        double[] roundTotals = new double[n];
        for (int i = 0; i < n; i++) {
            List<RoundResult.CaseRoundScore> rCases = rounds.get(i).getCases();
            roundTotals[i] = rCases.stream()
                    .mapToDouble(RoundResult.CaseRoundScore::getFinalScore)
                    .average().orElse(0.0);
        }
        double totalMean = mean(roundTotals);
        double totalStdev = stdev(roundTotals, totalMean);

        // Veto count across the last round (for snapshot purposes)
        long vetoCount = rounds.get(n - 1).getCases().stream()
                .filter(RoundResult.CaseRoundScore::isVeto).count();

        return RoundedReport.builder()
                .numRounds(n)
                .cases(aggregatedCases)
                .strategyMeans(strategyMeans)
                .roundTotalScores(roundTotals)
                .totalMean(roundTo2(totalMean))
                .totalStdev(roundTo2(totalStdev))
                .vetoCount(vetoCount)
                .build();
    }

    // ---------- stats helpers ----------

    /** Population standard deviation (not sample). */
    static double stdev(double[] values, double mean) {
        if (values.length <= 1) return 0.0;
        double sumSq = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / values.length);
    }

    static double mean(double[] values) {
        if (values.length == 0) return 0.0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    static double roundTo2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Accumulator for per-dimension scores across rounds. */
    private static class DimAgg {
        private final List<Double> vals = new ArrayList<>();
        void add(double v) { vals.add(v); }
        double[] values() { return vals.stream().mapToDouble(Double::doubleValue).toArray(); }
    }

    // ---------- value types ----------

    @Value
    @Builder
    public static class AggregatedCaseScore {
        String caseId;
        String strategy;
        /** Raw per-round final scores (for report matrix). */
        double[] scores;
        /** Mean across rounds. */
        double mean;
        /** Stdev across rounds. */
        double stdev;
        /** Per-dimension aggregation: dimName → [mean, stdev]. */
        Map<String, double[]> perDim;
    }

    @Value
    @Builder
    public static class RoundedReport {
        int numRounds;
        List<AggregatedCaseScore> cases;
        /** strategy → [mean, stdev]. */
        Map<String, double[]> strategyMeans;
        /** Per-round total mean scores (length = numRounds). */
        double[] roundTotalScores;
        /** Grand total mean across all rounds. */
        double totalMean;
        /** Stdev of per-round totals. */
        double totalStdev;
        /** Veto count from last round. */
        long vetoCount;
    }
}