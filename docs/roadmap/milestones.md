# Milestones

> 项目总路线图。22 天 / 8 Phase / 4 个面试爆点。每完成一个 Phase 必须更新本表。

---

## 总览

| Phase | 名称 | 工时 | 状态 | 关键产出 | 面试价值 |
| --- | --- | --- | --- | --- | --- |
| **0** | 评测基线 | 2d | ✅ Done | 25 case + 项目重命名（ADR-0009）+ Evaluator 端到端 + baseline（stub 模式） | ⭐⭐⭐⭐⭐ |
| **1** | 模块化单体收敛 | 2d | ⚪ Pending | ADR-0001 + 删除 microservice | ⭐⭐⭐ |
| **2** | 删 LangChain4j Patch | 2d | ⚪ Pending | ADR-0002 + 框架升级 | ⭐⭐⭐⭐ |
| **3** | Tool 安全体系 | 4d | ⚪ Pending | ADR-0004 + 20 单测 | ⭐⭐⭐⭐⭐ |
| **4** | AI Router 重写 | 4d | ⚪ Pending | ADR-0003 + 路由埋点 | ⭐⭐⭐⭐⭐ |
| **5** | Eval 体系自动化 | 5d | ⚪ Pending | ADR-0005 + CI 回归 | ⭐⭐⭐⭐⭐ |
| **6** | 质量指标埋点 | 3d | ⚪ Pending | metric 表 + SQL 报表 | ⭐⭐⭐⭐ |
| **7** | 收尾 | 2d | ⚪ Pending | ADR-0006/0007 + README | ⭐⭐⭐ |
| | **合计** | **22d** | | | |

---

## Phase 详情

### Phase 0 · 评测基线（2d）

**目标**：建立质量评估的"尺子"，作为后续所有重构的回归基准。

- 完成标准：见 [`current-phase.md`](./current-phase.md)
- 关键产出：`eval/cases/*.yaml` ×25，`eval/reports/baseline.md`
- ADR：ADR-0008（已 Accepted）

---

### Phase 1 · 模块化单体收敛（2d）

**目标**：删除微服务双份代码，主线包按领域分模块。

- 删除目标：`yu-ai-code-mother-microservice/`（用 git mv 到 legacy/ 或直接 rm，由 ADR-0001 决定）
- 包重组：`com.prompt2app` → `app / router / agent / eval / metric / infra`
- 关键产出：ADR-0001
- DoD：见 [`../governance/definition-of-done.md`](../governance/definition-of-done.md#phase-1--模块化单体收敛)

---

### Phase 2 · 删 LangChain4j Patch（2d）

**目标**：删除 `dev/langchain4j/` 源码覆盖包，升级到稳定版。

- 删除目标：`src/main/java/dev/langchain4j/`
- 关键产出：ADR-0002
- DoD：见 governance/definition-of-done.md

---

### Phase 3 · Tool 安全体系（4d）⭐ 面试爆点

**目标**：三层防御 + 20 单测进 CI。

- 三层：Schema 校验 / 工作目录绑定 / 调用次数熔断
- 关键产出：ADR-0004，`src/test/java/.../tool/safety/*Test.java`
- DoD：20+ 测试全过、CI 红线

---

### Phase 4 · AI Router 重写（4d）⭐ 面试爆点

**目标**：规则 + LLM 兜底两层路由 + 决策埋点。

- 关键产出：ADR-0003
- 评测：路由准确率与 case `expected_strategy` 对比

---

### Phase 5 · Eval 体系自动化（5d）⭐ 面试爆点

**目标**：评测执行器升级，CI 自动回归。

- 三维评分：编译 / 渲染 / LLM-Judge
- 关键产出：ADR-0005，`eval/reports/<sha>.md` 自动生成

---

### Phase 6 · 质量指标埋点（3d）

**目标**：12 维 `generation_metric` 表 + 一页 SQL 报表。

- 关键产出：表结构 + 报表页（不上 Grafana）
- 相关 ADR：可能涉及 ADR-0006

---

### Phase 7 · 收尾（2d）

- 写 ADR-0006（不上 MinIO） + ADR-0007（不引入 Workflow）
- README.md 改写为面向用户/招聘官版本
- 简历段落定稿

---

## 总产出清单（项目结束时应有）

- ✅ 一个能跑的模块化单体（Spring Boot 3 + Vue 3）
- ✅ 25 case 评测集 + CI 回归
- ✅ 8 篇 ADR（编号 0001-0008）
- ✅ 1 张 metric 表 + 1 个 SQL 报表页
- ✅ 一段简历 + 4 个 5 分钟面试故事
- ✅ 完整的治理体系（Charter / Working Rules / DoD / ACP / Tasks）

---

## 状态变更记录

| 日期 | Phase | 旧状态 | 新状态 | 备注 |
| --- | --- | --- | --- | --- |
| 2026-06-18 | Phase 0 | Pending | In Progress | 项目重构启动，分支 feature/ai-engineering-rebuild 创建 |
| 2026-06-18 | Phase 0 | — | （越界） | ADR-0009 批准项目重命名为 Prompt2App，已完成 |
| 2026-06-18 | Phase 0 | — | （进度） | 25 case 全部就位（HTML 7 / MultiFile 8 / Vue 10），剩余执行器 + baseline |
| 2026-06-18 | Phase 0 | In Progress | **Done** | Evaluator 9/9 测试通过；baseline.md（stub 模式）生成；mvn compile 已验证（JDK 21 + Lombok） |
