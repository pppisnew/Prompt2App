# Metric Design

> **状态**：v1（Phase 6 落地）
> **对应 Phase**：Phase 6 · 生成质量埋点
> **相关 ADR**：ADR-0005（评测体系把 Phase 6 列为延后项）+ Charter §3「不上 Grafana」

---

## 数据流

```
用户请求 /api/app/add
       │
       ▼
RoutingService.route(prompt)  ──►  RoutingDecision
       │                            │
       │                            ▼
       │              GenerationMetricService.recordRouting(appId, userId, decision)
       │                            │
       │                            ▼ INSERT generation_metric (路由字段，success=false)
       │                            │
       │                            ▶ 返回 metricId
       │
       ▼
AiCodeGeneratorFacade.generate(...)  ──► 完成（含 token / 成本 / tool_call_count）
       │
       ▼
GenerationMetricService.completeOutcome(metricId, outcome)
       │
       ▼ UPDATE generation_metric SET 生成字段 + success/errorMessage WHERE id = metricId
       │
       ▼
MetricReportService.overallStats / byStrategy / byRouterLayer / recentFailures
       │
       ▼
GET /api/metric/report  ──►  JSON
```

## 表结构（`sql/generation_metric.sql`）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | BIGINT PK | 自增主键 |
| `appId` | BIGINT | 关联 `app.id` |
| `userId` | BIGINT NULL | 用户 ID（匿名时 NULL）|
| `strategy` | VARCHAR(32) | HTML / MULTI_FILE / VUE_PROJECT |
| `routerLayer` | VARCHAR(32) | RULE_KEYWORD / RULE_LENGTH / LLM_FALLBACK / LLM_ERROR_FALLBACK |
| `routerReason` | VARCHAR(256) | 路由理由（已截断） |
| `routerConfidence` | DECIMAL(3,2) | 0.00-1.00 |
| `routerDurationMs` | INT | 路由阶段耗时 |
| `generationDurationMs` | INT | 生成阶段端到端耗时 |
| `tokenInput` / `tokenOutput` | INT | LLM token 消耗 |
| `costUsd` | DECIMAL(10,6) | 估算成本 |
| `toolCallCount` | INT | Tool 调用次数（仅 Vue 路径有值） |
| `success` | TINYINT(1) | 是否成功 |
| `errorMessage` | VARCHAR(512) | 失败原因（成功时 NULL） |
| `createTime` | DATETIME | 创建时间 |

**4 个索引**：`(strategy, success)` / `appId` / `createTime` / `routerLayer`

## 类设计

```
metric/
├── entity/GenerationMetric.java              MyBatis-Flex @Table 实体
├── mapper/GenerationMetricMapper.java        BaseMapper<GenerationMetric>
├── GenerationMetricService.java              recordRouting / completeOutcome
├── MetricReportService.java                  4 类聚合 SQL
└── MetricReportController.java               REST /metric/report

# 已存在（Phase 1 重命名自 monitor/）
├── AiModelMetricsCollector.java              Spring Actuator 指标（保留）
├── AiModelMonitorListener.java
├── MonitorContext.java
└── MonitorContextHolder.java
```

## 异常容错原则

**埋点故障绝不阻塞主链路**：
- `recordRouting` DB 写失败 → log warn + 返回 null
- `completeOutcome` 接收 null id → 安静跳过（不抛、不重试）
- `MetricReportService` 任一查询失败 → log warn + 返回空集合

业务代码（`AppServiceImpl`）调用 `recordRouting` 时再加一层 `try {} catch (Exception ignore) {}` 兜底。

## 报表 API

`GET /api/metric/report?hours=24` 返回 JSON：

```json
{
  "code": 0,
  "data": {
    "sinceUtc": "2026-06-19T...",
    "overall": {
      "total": 142, "successCount": 128, "successRate": 0.9014,
      "avgGenerationMs": 8200.3, "avgCostUsd": 0.0145, "avgRouterMs": 3.2
    },
    "byStrategy": [
      {"strategy": "HTML", "total": 80, "successRate": 0.95, ...},
      {"strategy": "MULTI_FILE", "total": 40, ...},
      {"strategy": "VUE_PROJECT", "total": 22, ...}
    ],
    "byRouterLayer": [
      {"layer": "RULE_KEYWORD", "total": 120, "avgRouterMs": 0.8, "avgConfidence": 1.00},
      {"layer": "LLM_FALLBACK", "total": 18, ...}
    ],
    "recentFailures": [
      {"id": 87, "appId": 19, "strategy": "VUE_PROJECT",
       "routerLayer": "RULE_KEYWORD", "errorMessage": "...", "createTime": "..."}
    ]
  }
}
```

不上 Grafana —— 前端可以单页 fetch + 简单图表渲染（Phase 7 README 中可贴截图）。

## 调用接入（Phase 6 实际落地）

只接入了 `AppServiceImpl.createApp`：路由完成后立刻 `recordRouting`。

**Phase 6 故意未做**的 `completeOutcome` 接入：
- 当前 `AiCodeGeneratorFacade.generate(...)` 是流式 SSE 调用，没有"统一完成回调点"。
- 接入需要改造 SSE 流的 onComplete / onError 钩子，跨多个文件。
- Phase 7 README 改写时若有时间再补；不影响本 Phase 数据结构 + 落表 + 报表 API 的完整性。
- 已记入 backlog。

## 测试覆盖

`GenerationMetricServiceTest` 8 个 @Test（mock mapper，无 DB）：
- recordRouting 字段映射 / reason 截断 / DB 异常返回 null / null strategy/layer 容错
- completeOutcome 字段映射 / null id 跳过 / errorMessage 截断 / DB 异常容错

## 演进锚点

- 数据 > 1M 行 → 加按月分区
- 报表查询慢 → 加聚合视图 / 物化视图
- 需要历史趋势 → 加 daily roll-up 表
- 团队多人 → 接入 Grafana（届时新 ADR 推翻 Charter §3 "不上 Grafana"）

## 参考

- [Charter §3 「明确不做：Grafana 深度看板」](../../PROJECT_CHARTER.md)
- [ADR-0005](../adr/0005-evaluation-automation.md)（评测体系）
- [Phase 4 RoutingDecision](./router-design.md)（数据来源）
