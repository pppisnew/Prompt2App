-- Phase 6 · 生成质量埋点表（ADR 在 task record / metric-design.md）
-- 每次代码生成请求 → 一行记录。

USE yu_ai_code_mother;

-- 生成质量指标表
CREATE TABLE IF NOT EXISTS generation_metric
(
    id                    BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    appId                 BIGINT       NOT NULL COMMENT '应用 ID',
    userId                BIGINT       NULL COMMENT '用户 ID（匿名时为 null）',
    -- 路由层（Phase 4 RoutingDecision）
    strategy              VARCHAR(32)  NOT NULL COMMENT '生成策略：HTML / MULTI_FILE / VUE_PROJECT',
    routerLayer           VARCHAR(32)  NULL COMMENT '路由层：RULE_KEYWORD / RULE_LENGTH / LLM_FALLBACK / LLM_ERROR_FALLBACK',
    routerReason          VARCHAR(256) NULL COMMENT '路由理由（截断到 256 字）',
    routerConfidence      DECIMAL(3, 2) NULL COMMENT '路由置信度 0.00-1.00',
    routerDurationMs      INT          NULL COMMENT '路由阶段耗时 ms',
    -- 生成阶段（Phase 3 + Phase 5 数据）
    generationDurationMs  INT          NULL COMMENT '生成阶段端到端耗时 ms',
    tokenInput            INT          NULL COMMENT 'LLM 输入 token 数',
    tokenOutput           INT          NULL COMMENT 'LLM 输出 token 数',
    costUsd               DECIMAL(10, 6) NULL COMMENT '估算成本 USD',
    toolCallCount         INT          NULL COMMENT 'Agent tool 调用次数（Vue 路径才有值）',
    -- 结果
    success               TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功（编译 / 渲染 / 启动 OK）',
    errorMessage          VARCHAR(512) NULL COMMENT '失败原因（成功时为 null）',
    -- 时间戳
    createTime            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 索引
    INDEX idx_strategy_success (strategy, success),
    INDEX idx_app_id (appId),
    INDEX idx_create_time (createTime),
    INDEX idx_router_layer (routerLayer)
) COMMENT '生成质量指标表（Phase 6）';
