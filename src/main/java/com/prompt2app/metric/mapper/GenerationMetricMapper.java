package com.prompt2app.metric.mapper;

import com.mybatisflex.core.BaseMapper;
import com.prompt2app.metric.entity.GenerationMetric;

/**
 * 生成质量指标 映射层（Phase 6）。
 *
 * <p>注意：本 mapper 在 {@link Prompt2AppApplication} 的 {@code @MapperScan("com.prompt2app.app.mapper")}
 * 之外（位于 {@code metric/mapper}）。Phase 1 ADR-0001 的 MapperScan 策略是"领域优先"——业务 mapper 在 app/，
 * 监控 mapper 在 metric/。需要在 main class 上扩展扫描范围或本接口加 {@code @Mapper} 注解。
 */
public interface GenerationMetricMapper extends BaseMapper<GenerationMetric> {
}
