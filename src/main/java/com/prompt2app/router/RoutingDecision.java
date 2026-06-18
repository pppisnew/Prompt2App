package com.prompt2app.router;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import lombok.Builder;
import lombok.Value;

/**
 * 路由决策结果。每个用户 prompt 的路由都产生一个 {@code RoutingDecision}，
 * 包含选中的策略 + 路由层 + 理由 + 置信度，供日志 / 后续 Phase 6 落表 / 评测分析使用。
 *
 * <p>详见 ADR-0003。
 */
@Value
@Builder
public class RoutingDecision {

    /** 路由分层。 */
    public enum Layer {
        /** Layer 1：关键词命中。 */
        RULE_KEYWORD,
        /** Layer 1：字数判定（无明显关键词 + 短 prompt → HTML）。 */
        RULE_LENGTH,
        /** Layer 2：LLM 兜底，正常返回。 */
        LLM_FALLBACK,
        /** Layer 2：LLM 抛错时退到 HTML 兜底。 */
        LLM_ERROR_FALLBACK
    }

    /** 最终选中的代码生成策略。 */
    CodeGenTypeEnum strategy;

    /** 由哪一层做出的决策。 */
    Layer layer;

    /**
     * 决策理由文本。
     * 例：{@code "matched VUE keyword '看板'"} / {@code "prompt length 30 < 50, no complex keyword"} /
     * {@code "rule miss; LLM classification"}.
     */
    String reason;

    /** 置信度 [0, 1]。规则=1.0；LLM=0.6；LLM 异常=0.3。 */
    double confidence;

    /** 端到端耗时（ms）。 */
    long durationMs;

    /** 路由时使用的原始 prompt（用于溯源；正式入表时可以截断）。 */
    String userPrompt;
}
