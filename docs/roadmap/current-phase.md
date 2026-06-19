# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 5 · 评测体系自动化**（⭐⭐⭐⭐⭐ 面试爆点 #3）

- **状态**：✅ DoD 全部勾选
- **开始日期**：2026-06-19
- **完成日期**：2026-06-19
- **责任人**：项目作者
- **上一 Phase**：Phase 4 ✅ Done（AI Router 两层 + 17 单测 / 80% 准确率）

---

## 目标

把 Phase 0 的"评测脚手架（stub mode）"升级到生产级**三维评分自动化体系**：

```
[Phase 0]  load → stub invoker → mustContain check → markdown
[Phase 5]  load → invoker → [Compile / Render / LLM-Judge 三维评分]
                          ↓
                    diff vs prev baseline → CI 红线
```

详细决策见 [`docs/adr/0005-evaluation-automation.md`](../adr/0005-evaluation-automation.md)。

---

## 完成标准（Definition of Done）

引用自 [`definition-of-done.md`](../governance/definition-of-done.md#phase-5--评测体系自动化)：

- [x] 评测执行器升级：支持批量跑 + 三维评分（plug-in scorers）✅
- [x] LLM-as-Judge 实现 ✅（解析容错 + 异常兜底；live mode 通过 EVAL_LIVE=1 触发，不进 CI）
- [x] HTML 渲染检查 ✅（`RenderScorer` 三策略，无 Playwright）
- [x] CI 集成 ✅（`.github/workflows/eval.yml` 含 eval / router / safety 三大门控）
- [x] Markdown 报表自动生成 + diff against prev baseline ✅
- [x] ADR-0005 Accepted ✅

---

## 允许做的事 ✅

- 在 `eval/` 包下新增 Scorer 接口 + 三种实现 + Composite + DiffReporter
- 引入 `langchain4j` 现有 LlmJudge AiService（不增新依赖）
- 新增 `.github/workflows/eval.yml`（CI 配置）
- 修改 `EvalRunner` 编排逻辑
- 修改 `MarkdownReporter` 输出格式（支持三维）
- 新增测试

## 禁止做的事 ❌

- 引入 Playwright 依赖（延后到未来 Phase，见 ADR-0005 §代价）
- 修改 25 个评测 case 内容（数据基准锁定）
- 新增其它 Maven 依赖（除非已有 langchain4j 子包，且走说明）
- 启动 Phase 6 metric 入表

---

## 风险与已知阻塞

- **真 LLM-Judge 调用成本**：每次评测 25 case × 3 次稳定性 = 75 次 LLM 调用，估算 < $1。CI 中默认 stub 模式，"live" 模式靠环境变量开
- **Playwright 在 CI 复杂**：浏览器二进制 + 系统依赖。本 Phase 用 JSoup-free 的字符串/正则做 HTML 健康检查，足够覆盖"render_non_empty"维度
- **DiffReporter 首次跑无 prev baseline**：写 fallback 让"无 prev"等同于"全部通过"
- **24 个 SpringBootTest 集成测试**：仍未修复，沿用 Phase 1 backlog

---

## 下一 Phase 预告

**Phase 6 · 生成质量埋点**（3d）

- `generation_metric` 表 + 12 维字段
- 一页 SQL 报表（成功率 / 平均耗时 / 路由偏差率）
- 落 RoutingDecision / EvalScore / Tool 调用计数
- 写 ADR-0006（如涉及 OSS 决策）
