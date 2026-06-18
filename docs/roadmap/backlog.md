# Backlog

> 已发现但**不在当前 Phase 范围内**的工作。这里只**记录**，不**执行**。
> 未来对应 Phase 启动时，从本文件取项目，确认后移入 [`current-phase.md`](./current-phase.md)。

---

## 录入规则

- 任何在执行任务时冒出的"顺便也改一下"想法，立刻写到这里，**不要顺手做**。
- 每条至少包含：发现日期 / 简述 / 触发场景 / 推测应在哪个 Phase 处理。
- 不写 `nice-to-have` —— 价值不明的想法直接丢，不要污染 backlog。

---

## Phase 1 范围（待 Phase 1 启动时取出）

- **2026-06-18** · 前端目录重命名：`yu-ai-code-mother-frontend/` → `prompt2app-frontend/`。当前 ADR-0009 决定不动文件系统目录名（避免 break IDE 配置 / 相对路径），Phase 1 与模块化重组一起做。
- **2026-06-18** · `mvn compile -DskipTests` 兜底验证：在 Phase 1 启动前跑一次，定位 ADR-0009 重命名可能遗漏的字符串残留。

## Phase 2 范围

- _（暂无）_

## Phase 3 范围

- _（暂无）_

## Phase 4 范围

- _（暂无）_

## Phase 5 范围

- _（暂无）_

## Phase 6 范围

- _（暂无）_

## Phase 7 范围

- **2026-06-18** · 决定不引入 Workflow，但需要在 ADR-0007 里写明"曾评估过 Plan-Execute-Review 三节点方案，决定不做的具体理由"——避免日后被问起没有论据。
- **2026-06-18** · README.md 改写——目前是教程版本，结尾时改写为"面向招聘官的项目介绍"。

---

## 跨 Phase / 待评估

- **2026-06-18** · `yu-ai-code-mother-frontend/` 的 ADR：是否在 Phase 1 一并评估前端的目录结构？暂记，等 Phase 1 启动时决定。
- **2026-06-18** · `grafana/` 与 `prometheus.yml` 是否删除：与 Charter §3「不上 Grafana」一致，但不是当前 Phase 工作；Phase 7 收尾时一并清理。

---

## 已驳回（Rejected）

> 写到这里的想法表示**已评估并明确不做**，配套 ADR 或显式理由。

- _（暂无）_
