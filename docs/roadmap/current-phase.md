# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 6 · 生成质量埋点**

- **状态**：✅ DoD 全部勾选
- **开始日期**：2026-06-19
- **完成日期**：2026-06-19
- **责任人**：项目作者
- **上一 Phase**：Phase 5 ✅ Done（评测体系自动化 + 33 单测 + CI 三大门控）

---

## 目标

把 Phase 4 的 `RoutingDecision` + Phase 5 的 `CaseFinalScore` + Phase 3 的 ToolCallCounter 数据**落表**：

```
用户请求
   ↓
RoutingService.route()  →  RoutingDecision
   ↓                        ↓ Phase 6: 入表 generation_metric
AiCodeGenerator         →  生成完成（含 tool 调用次数 / token / 成本）
   ↓                        ↓ Phase 6: 完整字段 update
generation_metric 表
   ↓
一页 SQL 报表（成功率 / 平均耗时 / 路由层分布 / 路由偏差率）
```

按 Charter §3「不上 Grafana」—— 一页 SQL 报表 + 简单 REST 接口足够。

---

## 完成标准（Definition of Done）

引用自 [`definition-of-done.md`](../governance/definition-of-done.md#phase-6--生成质量埋点)：

- [x] `generation_metric` 表创建 ✅（15 字段，4 索引）
- [x] 每次生成请求落表 ✅（AppServiceImpl 接入 recordRouting；completeOutcome 接入推到 backlog）
- [x] 一页 SQL 报表服务 ✅（4 类聚合 + REST `/api/metric/report`）
- [x] 不引入 Grafana ✅
- [x] 单测覆盖 ✅（GenerationMetricServiceTest 8/8）
- [x] `architecture/metric-design.md` 升到 v1 ✅

---

## 允许做的事 ✅

- 在 `metric/` 下新增 `entity / mapper / GenerationMetricService / MetricReportService`
- `sql/` 下新增建表 SQL
- 修改 `RoutingService` / `AppServiceImpl` 接入 `record()` 调用
- 新增简单 REST endpoint（`/api/metric/report`）
- 修改 `application.yml`（如新增配置项）

## 禁止做的事 ❌

- 引入 Grafana / Prometheus 新组件（按 Charter §3）
- 修改 evaluator 业务（Phase 5 已闭环）
- 改其它 phase 代码
- 启动 Phase 7 收尾

---

## 风险与已知阻塞

- **写表需要 DB**：单测用 in-memory H2 或 mock；不依赖真 MySQL
- **MyBatis-Flex 在测试环境**：现有测试都不需要数据库；Phase 6 测试通过 mock mapper 解决
- **历史数据**：Phase 6 之前没埋点，所以新表"从今天开始有数据"。这是预期。
- **24 个 SpringBootTest 集成测试**：仍未修复，沿用 backlog

---

## 下一 Phase 预告

**Phase 7 · 收尾**（2d）

- 写 ADR-0006（不上 MinIO）
- 写 ADR-0007（不引 Workflow）
- README.md 改写为面向招聘官版本
- 简历段落定稿
