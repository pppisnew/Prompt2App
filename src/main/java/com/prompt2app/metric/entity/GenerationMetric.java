package com.prompt2app.metric.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生成质量指标实体（Phase 6 · ADR-0005 落地）。
 *
 * <p>每次代码生成请求一行：路由阶段 + 生成阶段 + 结果。
 *
 * <p>字段总览（12+）：
 * <ul>
 *   <li>关联：{@code appId}, {@code userId}</li>
 *   <li>路由：{@code strategy}, {@code routerLayer}, {@code routerReason}, {@code routerConfidence}, {@code routerDurationMs}</li>
 *   <li>生成：{@code generationDurationMs}, {@code tokenInput}, {@code tokenOutput}, {@code costUsd}, {@code toolCallCount}</li>
 *   <li>结果：{@code success}, {@code errorMessage}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("generation_metric")
public class GenerationMetric implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 应用 ID（关联 app.id）。 */
    private Long appId;

    /** 用户 ID（匿名场景为 null）。 */
    private Long userId;

    // ---- routing stage (Phase 4 RoutingDecision) ----

    /** HTML / MULTI_FILE / VUE_PROJECT */
    private String strategy;

    /** RULE_KEYWORD / RULE_LENGTH / LLM_FALLBACK / LLM_ERROR_FALLBACK */
    private String routerLayer;

    /** 路由理由（已截断）。 */
    private String routerReason;

    /** 0.00-1.00 */
    private BigDecimal routerConfidence;

    /** 路由阶段耗时（ms）。 */
    private Integer routerDurationMs;

    // ---- generation stage ----

    /** 生成阶段端到端耗时（ms）。 */
    private Integer generationDurationMs;

    /** LLM 输入 token 数。 */
    private Integer tokenInput;

    /** LLM 输出 token 数。 */
    private Integer tokenOutput;

    /** 估算成本（USD）。 */
    private BigDecimal costUsd;

    /** Tool 调用次数（仅 Vue 路径）。 */
    private Integer toolCallCount;

    // ---- outcome ----

    /** 是否成功。 */
    private Boolean success;

    /** 失败原因（成功时为 null）。 */
    private String errorMessage;

    // ---- timestamp ----

    @Column(value = "createTime", onInsertValue = "now()")
    private LocalDateTime createTime;
}
