package com.prompt2app.eval;

import lombok.Builder;
import lombok.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评测回归报表：把当前评测结果与上一次 baseline 比对。
 *
 * <p>读上一份 {@code eval/reports/*.md}（最近一次的快照），按 case id 抽取 final 分数，
 * 与本次比较。
 *
 * <p>规则（ADR-0005 §实施细节 §6）：
 * <ul>
 *   <li>单 case 分数下降 &gt; 10 → regressed ⚠️</li>
 *   <li>平均分下降 &gt; 5 (绝对值) → 总体红灯</li>
 *   <li>无 prev baseline → 首次跑，全部 "new"</li>
 * </ul>
 */
public class DiffReporter {

    /** 单 case 分数下降阈值（绝对值），超过则标记 regressed。 */
    private static final double CASE_REGRESSION_THRESHOLD = 10.0;
    /** 总平均分下降阈值（绝对值），超过则总体红灯。 */
    private static final double OVERALL_REGRESSION_THRESHOLD = 5.0;

    /** 抓取 baseline.md 表格行 "| 003-todo-app-vue | ... | XX.X |" 末尾的 final 分。 */
    private static final Pattern BASELINE_ROW = Pattern.compile(
            "^\\|\\s*([\\w\\-]+)\\s*\\|.*\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\|\\s*$",
            Pattern.MULTILINE);

    /**
     * 比对当前结果 vs 上一次 baseline。
     *
     * @param currentScores  当前各 case 最终分
     * @param prevReport     上一次 baseline.md 路径；不存在视为首次跑
     * @return 整体回归判定 + 单 case 列表
     */
    public DiffReport diff(List<CompositeScorer.CaseFinalScore> currentScores, Path prevReport) {
        Map<String, Double> prev = loadPrev(prevReport);
        List<CaseDiff> rows = new ArrayList<>(currentScores.size());

        double currentSum = 0.0;
        double prevSum = 0.0;
        int prevCount = 0;
        int regressedCount = 0;

        for (CompositeScorer.CaseFinalScore cur : currentScores) {
            Double prevScore = prev.get(cur.getCaseId());
            String status;
            double delta;
            if (prevScore == null) {
                status = "new";
                delta = 0.0;
            } else {
                delta = cur.getFinalScore() - prevScore;
                if (delta < -CASE_REGRESSION_THRESHOLD) {
                    status = "regressed";
                    regressedCount++;
                } else if (delta > CASE_REGRESSION_THRESHOLD) {
                    status = "improved";
                } else {
                    status = "stable";
                }
                prevSum += prevScore;
                prevCount++;
            }
            rows.add(CaseDiff.builder()
                    .caseId(cur.getCaseId())
                    .prev(prevScore)
                    .cur(cur.getFinalScore())
                    .delta(delta)
                    .status(status)
                    .build());
            currentSum += cur.getFinalScore();
        }

        double currentAvg = currentScores.isEmpty() ? 0.0 : currentSum / currentScores.size();
        Double prevAvg = prevCount == 0 ? null : prevSum / prevCount;
        double avgDelta = prevAvg == null ? 0.0 : currentAvg - prevAvg;
        boolean overallRegressed = prevAvg != null && avgDelta < -OVERALL_REGRESSION_THRESHOLD;

        return DiffReport.builder()
                .rows(rows)
                .currentAvg(currentAvg)
                .prevAvg(prevAvg)
                .avgDelta(avgDelta)
                .regressedCaseCount(regressedCount)
                .overallRegressed(overallRegressed)
                .build();
    }

    /** 解析上一次 baseline.md 的 case 表行；找不到文件返回空 map。 */
    static Map<String, Double> loadPrev(Path prevReport) {
        if (prevReport == null || !Files.exists(prevReport)) {
            return Map.of();
        }
        Map<String, Double> map = new HashMap<>();
        try {
            String text = Files.readString(prevReport);
            Matcher m = BASELINE_ROW.matcher(text);
            while (m.find()) {
                String id = m.group(1);
                if (!id.matches("\\d{3}-.*")) {
                    // 跳过表头 / 分隔符（ID 必须是 NNN-xxx 格式）
                    continue;
                }
                try {
                    map.put(id, Double.parseDouble(m.group(2)));
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }
        } catch (IOException e) {
            // 读取失败不阻塞流程，按"无 prev"对待
            return Map.of();
        }
        return map;
    }

    /** Markdown 格式输出。 */
    public String render(DiffReport report) {
        StringBuilder sb = new StringBuilder(2 * 1024);
        sb.append("## Regression Diff (vs prev baseline)\n\n");
        if (report.getPrevAvg() == null) {
            sb.append("> No previous baseline found. This is the first run.\n\n");
        } else {
            sb.append("- **Current avg**: ").append(String.format(Locale.ROOT, "%.2f", report.getCurrentAvg())).append("\n");
            sb.append("- **Previous avg**: ").append(String.format(Locale.ROOT, "%.2f", report.getPrevAvg())).append("\n");
            sb.append("- **Δ**: ").append(String.format(Locale.ROOT, "%+.2f", report.getAvgDelta()));
            if (report.isOverallRegressed()) {
                sb.append(" 🚨 **OVERALL REGRESSION**");
            }
            sb.append("\n");
            sb.append("- **Regressed cases**: ").append(report.getRegressedCaseCount()).append("\n\n");
        }
        sb.append("| ID | Prev | Cur | Δ | Status |\n| --- | --- | --- | --- | --- |\n");
        for (CaseDiff r : report.getRows()) {
            sb.append("| ").append(r.getCaseId());
            sb.append(" | ").append(r.getPrev() == null ? "—" : String.format(Locale.ROOT, "%.1f", r.getPrev()));
            sb.append(" | ").append(String.format(Locale.ROOT, "%.1f", r.getCur()));
            sb.append(" | ").append(r.getPrev() == null ? "—" : String.format(Locale.ROOT, "%+.1f", r.getDelta()));
            sb.append(" | ");
            switch (r.getStatus()) {
                case "regressed": sb.append("🚨 regressed"); break;
                case "improved":  sb.append("✅ improved"); break;
                case "stable":    sb.append("➖ stable"); break;
                default:          sb.append("🆕 new"); break;
            }
            sb.append(" |\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    @Value
    @Builder
    public static class DiffReport {
        List<CaseDiff> rows;
        double currentAvg;
        /** null 时表示无 prev baseline。 */
        Double prevAvg;
        double avgDelta;
        int regressedCaseCount;
        boolean overallRegressed;
    }

    @Value
    @Builder
    public static class CaseDiff {
        String caseId;
        /** null 表示新增 case。 */
        Double prev;
        double cur;
        double delta;
        /** "new" | "stable" | "improved" | "regressed" */
        String status;
    }
}
