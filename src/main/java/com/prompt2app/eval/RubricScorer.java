package com.prompt2app.eval;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Locale;

/**
 * 确定性 rubric 评分器（{@link Scorer} 的实现之一）。
 *
 * <p>Phase 0 起就有的"确定性维度"。Phase 5 在保留向后兼容的 {@link #score(EvalCase, AgentInvoker.InvocationResult)}
 * 方法基础上，新增 {@link Scorer#evaluate(EvalCase, AgentInvoker.InvocationResult)} 实现，
 * 让它能与 {@link RenderScorer} / {@link LlmJudgeScorer} 通过 {@link CompositeScorer} 组合。
 *
 * <p>三个子检查（任一失败即视为否决项）：
 * <ol>
 *   <li>{@code mustContain} 命中率 ≥ 80%（少于 80% → 否决）</li>
 *   <li>{@code mustNotContain} 反模式 0 命中（一旦命中 → 否决）</li>
 *   <li>{@code minFiles} 数量校验（不达标 → 否决）</li>
 * </ol>
 *
 * <p>详见 ADR-0005 §实施细节。
 */
public class RubricScorer implements Scorer {

    /** 否决阈值：mustContain 命中率低于此比例 → veto = true。 */
    private static final double MUST_CONTAIN_VETO_THRESHOLD = 0.8;

    @Override
    public String name() {
        return "rubric";
    }

    /** Phase 5 新接口：返回单维度 {@link ScoreContribution}。 */
    @Override
    public ScoreContribution evaluate(EvalCase evalCase, AgentInvoker.InvocationResult invocation) {
        CaseScore old = score(evalCase, invocation);
        if (!old.isInvoked()) {
            return ScoreContribution.builder()
                    .dimension(name())
                    .score(0.0)
                    .veto(true)
                    .detail("not invoked (stub mode)")
                    .build();
        }

        double containRatio = old.getMustContainTotal() == 0
                ? 1.0
                : (double) old.getMustContainHits() / old.getMustContainTotal();
        boolean mustContainVeto = containRatio < MUST_CONTAIN_VETO_THRESHOLD;
        boolean mustNotVeto = old.getMustNotContainHits() > 0;
        boolean minFilesVeto = !old.isMinFilesOk();
        boolean veto = mustContainVeto || mustNotVeto || minFilesVeto;

        StringBuilder detail = new StringBuilder();
        detail.append(String.format(Locale.ROOT,
                "mustContain %d/%d (%.0f%%)",
                old.getMustContainHits(), old.getMustContainTotal(), containRatio * 100));
        detail.append(", mustNotContain ").append(old.getMustNotContainHits())
              .append("/").append(old.getMustNotContainTotal());
        detail.append(", minFiles ").append(old.isMinFilesOk() ? "ok" : "FAIL");
        if (veto) {
            detail.append(" → VETO");
        }

        return ScoreContribution.builder()
                .dimension(name())
                .score(old.getDeterministicScore())
                .veto(veto)
                .detail(detail.toString())
                .build();
    }

    /** Phase 0 起的旧接口（向后兼容，RubricScorerTest 使用）。 */
    public CaseScore score(EvalCase evalCase, AgentInvoker.InvocationResult invocation) {
        EvalCase.Rubric rubric = evalCase.getRubric();
        int mustContainTotal = safeSize(rubric == null ? null : rubric.getMustContain());
        int mustNotContainTotal = safeSize(rubric == null ? null : rubric.getMustNotContain());

        if (!invocation.isInvoked()) {
            return CaseScore.builder()
                    .invoked(false)
                    .mustContainHits(0)
                    .mustContainTotal(mustContainTotal)
                    .mustNotContainHits(0)
                    .mustNotContainTotal(mustNotContainTotal)
                    .minFilesOk(false)
                    .deterministicScore(0.0)
                    .note("Not invoked")
                    .build();
        }

        String haystackLower = invocation.getMergedOutput() == null
                ? ""
                : invocation.getMergedOutput().toLowerCase(Locale.ROOT);

        int mustContainHits = countMatches(rubric == null ? null : rubric.getMustContain(), haystackLower);
        int mustNotContainHits = countMatches(rubric == null ? null : rubric.getMustNotContain(), haystackLower);
        boolean minFilesOk = (rubric == null || rubric.getMinFiles() == null)
                || invocation.getFileCount() >= rubric.getMinFiles();

        // v0 评分：containRatio - notContainPenalty - minFilesPenalty, clamped to [0, 100]
        double containRatio = mustContainTotal == 0 ? 1.0 : (double) mustContainHits / mustContainTotal;
        double notContainPenalty = mustNotContainTotal == 0 ? 0.0 : (double) mustNotContainHits / mustNotContainTotal;
        double minFilesPenalty = minFilesOk ? 0.0 : 0.3;
        double raw = containRatio - notContainPenalty - minFilesPenalty;
        double clamped = Math.max(0.0, Math.min(1.0, raw));
        double score = clamped * 100.0;

        return CaseScore.builder()
                .invoked(true)
                .mustContainHits(mustContainHits)
                .mustContainTotal(mustContainTotal)
                .mustNotContainHits(mustNotContainHits)
                .mustNotContainTotal(mustNotContainTotal)
                .minFilesOk(minFilesOk)
                .deterministicScore(score)
                .note(null)
                .build();
    }

    private static int safeSize(List<String> list) {
        return list == null ? 0 : list.size();
    }

    private static int countMatches(List<String> needles, String haystackLower) {
        if (needles == null || needles.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String needle : needles) {
            if (needle == null || needle.isEmpty()) continue;
            if (haystackLower.contains(needle.toLowerCase(Locale.ROOT))) {
                n++;
            }
        }
        return n;
    }

    @Value
    @Builder
    public static class CaseScore {
        boolean invoked;
        int mustContainHits;
        int mustContainTotal;
        int mustNotContainHits;
        int mustNotContainTotal;
        boolean minFilesOk;
        /** 0-100 deterministic v0 score. */
        double deterministicScore;
        String note;
    }
}
