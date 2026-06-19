package com.prompt2app.eval;

import lombok.Builder;
import lombok.Value;

/**
 * 评测维度评分器接口（ADR-0005）。
 *
 * <p>每个 {@code Scorer} 实现一个**评测维度**：
 * <ul>
 *   <li>{@link RubricScorer} —— 确定性 must_contain / must_not_contain / minFiles 检查</li>
 *   <li>{@link RenderScorer} —— HTML 渲染非空检查（轻量级，无 Playwright）</li>
 *   <li>{@link LlmJudgeScorer} —— LLM-as-Judge 主观分</li>
 * </ul>
 *
 * <p>多个 Scorer 通过 {@link CompositeScorer} 组合，按"否决项 + 主分"规则求最终分。
 *
 * <p>详见 ADR-0005 §实施细节。
 */
public interface Scorer {

    /** Scorer 唯一标识，写入报表（"rubric" / "render" / "llm-judge"）。 */
    String name();

    /** 评分。失败也不抛异常 —— 失败编码到 {@link ScoreContribution}。 */
    ScoreContribution evaluate(EvalCase evalCase, AgentInvoker.InvocationResult invocation);

    /** 单维度评分贡献。 */
    @Value
    @Builder
    class ScoreContribution {
        /** 维度名（与 {@link #name()} 一致）。 */
        String dimension;
        /** 0-100 的本维度分数。 */
        double score;
        /** 是否触发"否决"——否决项任一失败 → 最终分 0。 */
        boolean veto;
        /** 文本说明，写入报表。 */
        String detail;
    }
}
