# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 4 · AI Router 重写**（⭐⭐⭐⭐⭐ 面试爆点 #2）

- **状态**：✅ DoD 全部勾选
- **开始日期**：2026-06-18
- **完成日期**：2026-06-18（一天内完成）
- **责任人**：项目作者
- **上一 Phase**：Phase 3 ✅ Done（Tool 安全三层 + 27 单测）

---

## 目标

把当前"裸 LLM 单层路由"升级为**规则 + LLM 兜底两层路由**：

```
[现状] userPrompt ──► LLM (routingChatModel) ──► CodeGenTypeEnum
[目标] userPrompt ──► [Layer 1 RuleRouter (毫秒级)]
                       │  规则命中 ─► RoutingDecision (RULE_*, conf=1.0)
                       └  规则未命中 ─► [Layer 2 LLM Fallback]
                                          └► RoutingDecision (LLM_FALLBACK, conf=0.6)
```

详细决策见 [`docs/adr/0003-ai-router-two-layer.md`](../adr/0003-ai-router-two-layer.md)。

---

## 完成标准（Definition of Done）

引用自 [`definition-of-done.md`](../governance/definition-of-done.md#phase-4--ai-router-重写)：

- [x] 规则路由层实现 ✅（关键词 + 字数 + N 页正则）
- [x] LLM 兜底层实现 ✅（保留现有 `AiCodeGenTypeRoutingService` 作 Layer 2 + LLM 异常 HTML 兜底）
- [x] 路由决策埋点 ✅（`RoutingDecision` 数据结构含 layer/reason/confidence/durationMs；日志输出，入库延后到 Phase 6）
- [x] 评测集回归 ≥ baseline ✅（**80% 准确率 + 88% 命中率**，超阈值 ≥60% / ≥80%）
- [x] 路由准确率指标输出 ✅（`RouterAccuracyTest` 自动跑 25 case 输出分布表）
- [x] ADR-0003 Accepted ✅

---

## 允许做的事 ✅

- 在 `router/` 下新建 `RuleRouter` / `RoutingService` / `RoutingDecision` 等类
- 在 `router/rule/` 下（如需要）拆分规则
- 修改 2 个调用点（`AppServiceImpl:132`、`RouterNode:29`）从直接调 LLM 改为调新 RoutingService
- 新增测试：单测 + 与评测集对齐验证
- 升级 `docs/architecture/router-design.md` 从 stub 到 v1

## 禁止做的事 ❌

- 修改 `AiCodeGenTypeRoutingService` 接口（保留作 Layer 2 LLM 实现）
- 改 prompt 模板（`prompt/codegen-routing-system-prompt.txt`）
- 入库 / 引入新依赖（落到 Phase 6）
- 改 evaluator
- 启动 Phase 5 评测体系自动化

---

## 风险与已知阻塞

- **规则粒度难调**：太严会漏召（rule miss 走 LLM 浪费 round-trip），太松会误召（用 HTML 处理本应 Vue 的复杂应用）。靠评测集 25 case 兜底
- **RouterNode 用 SpringContextUtil 取 Bean**：保留这个方式，避免改 workflow 内部
- **Layer 2 仍依赖 routingChatModelPrototype**：模型故障时 fallback to HTML 作为最后兜底
- **评测集 vs 真实分布**：25 case 是设计样本，规则在生产分布上的表现要等 Phase 5 自动化数据回归

---

## 下一 Phase 预告

**Phase 5 · 评测体系自动化**（5d，⭐⭐⭐⭐⭐ 面试爆点 #3）

- 评测执行器升级：支持批量跑 + 三维评分
- LLM-as-Judge + Playwright 渲染检查
- CI 集成：分数下降 > 5% 红灯
- 写 ADR-0005
