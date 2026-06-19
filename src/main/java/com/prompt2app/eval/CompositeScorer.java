package com.prompt2app.eval;

import com.prompt2app.eval.Scorer.ScoreContribution;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * 多维评分组合器（ADR-0005）。
 *
 * <p>合分规则：
 * <ul>
 *   <li>任一 Scorer 触发 {@code veto = true} → 最终分 0</li>
 *   <li>全部通过 → 取 LLM-Judge 主分（如果有 llm-judge 维度）</li>
 *   <li>无 LLM-Judge 维度 → 取所有维度算术平均</li>
 * </ul>
 *
 * <p>设计原则：**否决项严苛、主分柔软**。这样确定性维度（rubric / render）做"硬门控"，
 * LLM-Judge 维度做"软评分"，与业界 evals 实践一致。
 */
public class CompositeScorer {

    private final List<Scorer> scorers;

    public CompositeScorer(List<Scorer> scorers) {
        if (scorers == null || scorers.isEmpty()) {
            throw new IllegalArgumentException("at least one Scorer required");
        }
        this.scorers = List.copyOf(scorers);
    }

    public CaseFinalScore score(EvalCase evalCase, AgentInvoker.InvocationResult invocation) {
        List<ScoreContribution> contribs = new ArrayList<>(scorers.size());
        boolean anyVeto = false;
        ScoreContribution judge = null;
        double sum = 0.0;
        int countNonJudge = 0;

        for (Scorer s : scorers) {
            ScoreContribution c = s.evaluate(evalCase, invocation);
            contribs.add(c);
            if (c.isVeto()) {
                anyVeto = true;
            }
            if ("llm-judge".equals(c.getDimension())) {
                judge = c;
            } else {
                sum += c.getScore();
                countNonJudge++;
            }
        }

        double finalScore;
        String summary;
        if (anyVeto) {
            finalScore = 0.0;
            summary = "VETO by " + firstVetoer(contribs);
        } else if (judge != null) {
            finalScore = judge.getScore();
            summary = String.format(java.util.Locale.ROOT,
                    "all OK; final = LLM-Judge %.1f", finalScore);
        } else if (countNonJudge > 0) {
            finalScore = sum / countNonJudge;
            summary = String.format(java.util.Locale.ROOT,
                    "all OK; final = avg of %d non-judge dims %.1f", countNonJudge, finalScore);
        } else {
            finalScore = 0.0;
            summary = "no scorable dimensions";
        }

        return CaseFinalScore.builder()
                .caseId(evalCase.getId())
                .finalScore(finalScore)
                .veto(anyVeto)
                .contributions(contribs)
                .summary(summary)
                .build();
    }

    private static String firstVetoer(List<ScoreContribution> contribs) {
        for (ScoreContribution c : contribs) {
            if (c.isVeto()) return c.getDimension();
        }
        return "(none)";
    }

    @Value
    @Builder
    public static class CaseFinalScore {
        String caseId;
        /** 0-100 综合分。否决时为 0。 */
        double finalScore;
        boolean veto;
        List<ScoreContribution> contributions;
        String summary;
    }
}
