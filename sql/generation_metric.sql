-- Phase 6 · 生成质量埋点表（ADR 在 task record / metric-design.md）
-- 每次代码生成请求 → 一行记录。
--
-- 列名约定：snake_case，与 MyBatis-Flex entity + application.yml spring-boot 默认命名策略一致。
-- 历史教训：Phase 8 重命名数据库时此文件仅改了库名（yu_ai_code_mother → prompt2app），
-- 列名仍保留旧 camelCase（appId/userId/...），导致 INSERT 时报
-- "Unknown column 'app_id' in 'field list'"。2026-06-20 修正为 snake_case。

USE prompt2app;

-- 若已存在 camelCase 列名的旧表，先 DROP 再建（数据不可恢复，仅 dev 环境可接受）
DROP TABLE IF EXISTS generation_metric;

-- 生成质量指标表
CREATE TABLE generation_metric
(
    id                    BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    app_id                BIGINT       NOT NULL COMMENT '应用 ID',
    user_id               BIGINT       NULL COMMENT '用户 ID（匿名时为 null）',
    -- 路由层（Phase 4 RoutingDecision）
    strategy              VARCHAR(32)  NOT NULL COMMENT '生成策略：HTML / MULTI_FILE / VUE_PROJECT',
    router_layer          VARCHAR(32)  NULL COMMENT '路由层：RULE_KEYWORD / RULE_LENGTH / LLM_FALLBACK / LLM_ERROR_FALLBACK',
    router_reason         VARCHAR(256) NULL COMMENT '路由理由（截断到 256 字）',
    router_confidence     DECIMAL(3, 2) NULL COMMENT '路由置信度 0.00-1.00',
    router_duration_ms    INT          NULL COMMENT '路由阶段耗时 ms',
    -- 生成阶段（Phase 3 + Phase 5 数据）
    generation_duration_ms INT         NULL COMMENT '生成阶段端到端耗时 ms',
    token_input           INT          NULL COMMENT 'LLM 输入 token 数',
    token_output          INT          NULL COMMENT 'LLM 输出 token 数',
    cost_usd              DECIMAL(10, 6) NULL COMMENT '估算成本 USD',
    tool_call_count       INT          NULL COMMENT 'Agent tool 调用次数（Vue 路径才有值）',
    -- 结果
    success               TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功（编译 / 渲染 / 启动 OK）',
    error_message         VARCHAR(512) NULL COMMENT '失败原因（成功时为 null）',
    -- 时间戳
    -- 注意：entity 用 @Column(value = "createTime", onInsertValue = "now()") 显式映射，列名保持 camelCase
    createTime            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 索引
    INDEX idx_strategy_success (strategy, success),
    INDEX idx_app_id (app_id),
    INDEX idx_create_time (createTime),
    INDEX idx_router_layer (router_layer)
) COMMENT '生成质量指标表（Phase 6）';
