package com.prompt2app.metric;

import com.prompt2app.metric.entity.GenerationMetric;
import com.prompt2app.metric.mapper.GenerationMetricMapper;
import com.prompt2app.router.RoutingDecision;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 生成质量指标记录服务（Phase 6）。
 *
 * <p>提供两种调用模式：
 * <ol>
 *   <li>{@link #recordRouting} —— 路由阶段完成时调用，先落一行（仅路由字段），返回 metric ID</li>
 *   <li>{@link #completeOutcome} —— 生成阶段完成时调用，根据上一步 ID 把 outcome 字段补全</li>
 * </ol>
 *
 * <p>异常容错：DB 写入失败仅 log warn，**绝不抛**回业务，避免监控故障影响主链路。
 *
 * <p>详见 ADR-0005 / docs/architecture/metric-design.md。
 */
@Slf4j
@Service
public class GenerationMetricService {

    @Resource
    private GenerationMetricMapper mapper;

    /** 路由阶段完成时调用：写入路由层信息，返回 metric ID（用于后续 completeOutcome）。 */
    public Long recordRouting(Long appId, Long userId, RoutingDecision decision) {
        try {
            GenerationMetric m = GenerationMetric.builder()
                    .appId(appId)
                    .userId(userId)
                    .strategy(decision.getStrategy() == null ? null : decision.getStrategy().name())
                    .routerLayer(decision.getLayer() == null ? null : decision.getLayer().name())
                    .routerReason(truncate(decision.getReason(), 256))
                    .routerConfidence(BigDecimal.valueOf(decision.getConfidence()))
                    .routerDurationMs((int) Math.min(decision.getDurationMs(), Integer.MAX_VALUE))
                    .success(false) // 默认 false；completeOutcome 时更新
                    .build();
            mapper.insert(m);
            return m.getId();
        } catch (Exception e) {
            log.warn("[Metric] failed to record routing: {}", e.getMessage());
            return null;
        }
    }

    /** 生成阶段完成时调用：根据上一步 ID 补全 outcome 字段。 */
    public void completeOutcome(Long metricId, Outcome outcome) {
        if (metricId == null) {
            return; // recordRouting 失败时 metricId 为 null，安静跳过
        }
        try {
            GenerationMetric m = GenerationMetric.builder()
                    .id(metricId)
                    .generationDurationMs(outcome.generationDurationMs)
                    .tokenInput(outcome.tokenInput)
                    .tokenOutput(outcome.tokenOutput)
                    .costUsd(outcome.costUsd)
                    .toolCallCount(outcome.toolCallCount)
                    .success(outcome.success)
                    .errorMessage(truncate(outcome.errorMessage, 512))
                    .build();
            mapper.update(m);
        } catch (Exception e) {
            log.warn("[Metric] failed to complete outcome for id={}: {}", metricId, e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 生成阶段完成时的 outcome 数据。 */
    @lombok.Value
    @lombok.Builder
    public static class Outcome {
        Integer generationDurationMs;
        Integer tokenInput;
        Integer tokenOutput;
        BigDecimal costUsd;
        Integer toolCallCount;
        boolean success;
        String errorMessage;
    }
}
