# Metric Design

> **状态**：Stub（待 Phase 6 完成后撰写）
> **对应 Phase**：Phase 6 · 生成质量埋点
> **相关 ADR**：（无独立 ADR，挂在 Phase 6 Task Record 上）

---

## 设计要点（Phase 6 启动时展开）

### `generation_metric` 表（草案，12 维）

```sql
CREATE TABLE generation_metric (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id          BIGINT          NOT NULL,
  user_id         BIGINT          NOT NULL,
  -- 基础指标
  strategy        VARCHAR(32)     NOT NULL,   -- HTML / MULTI_FILE / VUE_PROJECT
  duration_ms     INT             NOT NULL,
  ttfb_ms         INT,                        -- SSE 首字节
  token_input     INT,
  token_output    INT,
  cost_usd        DECIMAL(10,6),
  tool_call_count INT,                        -- 仅 Agent 路径
  success         TINYINT(1)      NOT NULL,   -- 编译/启动是否成功
  -- 体验指标
  user_followup_count   INT DEFAULT 0,
  user_visual_edit_count INT DEFAULT 0,
  user_deployed         TINYINT(1) DEFAULT 0,
  -- 元数据
  router_layer    VARCHAR(16),                -- RULE / LLM
  router_reason   VARCHAR(256),
  created_at      DATETIME        NOT NULL,
  INDEX idx_strategy_success (strategy, success),
  INDEX idx_user_created (user_id, created_at)
);
```

### 报表（一页 SQL，不用 Grafana）

按 Charter §3，**不引入 Grafana**。提供单页 Vue 报表展示：

- 各 strategy 的成功率 / 平均耗时 / 平均成本（折线 + 柱状）
- 路由"事后正确率"（`expected_strategy` vs `strategy`）
- Top 10 高成本会话 / Top 10 失败会话

### 待详写

- [ ] 字段终稿（特别是 user 行为字段如何采集）
- [ ] 埋点切入点（AOP？Service 层显式调用？）
- [ ] 报表 SQL 与前端集成方式
- [ ] 数据保留策略（是否需要清理）

---

> _此处暂为占位。Phase 6 启动时按 [`README.md`](./README.md) §写作规范 撰写。_
