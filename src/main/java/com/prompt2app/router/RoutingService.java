package com.prompt2app.router;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.router.RoutingDecision.Layer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AI Router 编排服务（两层架构）。
 *
 * <ol>
 *   <li>Layer 1（{@link RuleRouter}）：规则命中即返回，毫秒级</li>
 *   <li>Layer 2（{@link AiCodeGenTypeRoutingService}）：LLM 兜底分类</li>
 *   <li>最终兜底：LLM 异常时返回 {@link CodeGenTypeEnum#HTML}（最便宜策略）</li>
 * </ol>
 *
 * <p>详见 ADR-0003。
 *
 * <p>所有路由决策都封装为 {@link RoutingDecision} 返回，包含层级 / 理由 / 置信度 /
 * 耗时，供日志、Phase 6 入表、评测集分析使用。
 */
@Slf4j
@Service
public class RoutingService {

    @Resource
    private RuleRouter ruleRouter;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory llmFactory;

    /** 完整路由：返回带元数据的 {@link RoutingDecision}。 */
    public RoutingDecision route(String userPrompt) {
        long start = System.currentTimeMillis();

        // Layer 1：规则
        Optional<RoutingDecision> ruleHit = ruleRouter.route(userPrompt);
        if (ruleHit.isPresent()) {
            RoutingDecision decision = withDuration(ruleHit.get(), start);
            log.info("[Router] {} ({}ms) prompt={}",
                    decision.getLayer(), decision.getDurationMs(), summarize(userPrompt));
            return decision;
        }

        // Layer 2：LLM 兜底
        try {
            AiCodeGenTypeRoutingService llmService = llmFactory.createAiCodeGenTypeRoutingService();
            CodeGenTypeEnum strategy = llmService.routeCodeGenType(userPrompt);
            if (strategy == null) {
                // LLM 返回 null（不该发生，但保守容忍）→ 走错误兜底
                throw new IllegalStateException("LLM returned null strategy");
            }
            RoutingDecision decision = RoutingDecision.builder()
                    .strategy(strategy)
                    .layer(Layer.LLM_FALLBACK)
                    .reason("rule miss; LLM classified as " + strategy.name())
                    .confidence(0.6)
                    .durationMs(System.currentTimeMillis() - start)
                    .userPrompt(userPrompt)
                    .build();
            log.info("[Router] {} ({}ms) → {} prompt={}",
                    decision.getLayer(), decision.getDurationMs(),
                    strategy.name(), summarize(userPrompt));
            return decision;
        } catch (Exception e) {
            // 最终兜底：LLM 异常 → HTML（最便宜，最不容易出问题）
            log.warn("[Router] LLM_ERROR_FALLBACK reason={}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return RoutingDecision.builder()
                    .strategy(CodeGenTypeEnum.HTML)
                    .layer(Layer.LLM_ERROR_FALLBACK)
                    .reason("LLM error → HTML fallback: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage())
                    .confidence(0.3)
                    .durationMs(System.currentTimeMillis() - start)
                    .userPrompt(userPrompt)
                    .build();
        }
    }

    /** 便捷方法：调用方只关心策略时使用。 */
    public CodeGenTypeEnum routeCodeGenType(String userPrompt) {
        return route(userPrompt).getStrategy();
    }

    private static RoutingDecision withDuration(RoutingDecision base, long start) {
        return RoutingDecision.builder()
                .strategy(base.getStrategy())
                .layer(base.getLayer())
                .reason(base.getReason())
                .confidence(base.getConfidence())
                .durationMs(System.currentTimeMillis() - start)
                .userPrompt(base.getUserPrompt())
                .build();
    }

    /** prompt 截断，避免日志过长。 */
    private static String summarize(String prompt) {
        if (prompt == null) return "(null)";
        String trimmed = prompt.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 57) + "...";
    }
}
