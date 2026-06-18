package com.prompt2app.eval;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Locale;

/**
 * Phase 0 deterministic rubric scoring.
 *
 * <p>Three components in v0:
 * <ol>
 *   <li>{@code mustContain} hit ratio — how many required strings appear.</li>
 *   <li>{@code mustNotContain} penalty — anti-pattern strings detected.</li>
 *   <li>{@code minFiles} gate — for MULTI_FILE / VUE_PROJECT cases.</li>
 * </ol>
 *
 * <p>Phase 5 will extend this with Playwright render check (page non-empty)
 * and LLM-as-Judge subjective scoring. Those land in {@code RenderScorer} and
 * {@code LlmJudgeScorer} when their own dependencies (Playwright, judge model)
 * are wired.
 */
public class RubricScorer {

    /** Score a case against an invocation result. Pure function — no I/O. */
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

        // v0 scoring: containRatio - notContainPenalty - minFilesPenalty, clamped to [0, 100].
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
        /** 0-100 deterministic v0 score. Phase 5 adds render and LLM-Judge dimensions. */
        double deterministicScore;
        String note;
    }
}
