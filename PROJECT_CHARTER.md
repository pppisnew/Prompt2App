# PROJECT CHARTER —— 项目宪法

> 这是本项目的最高约束文件。任何 AI 助手、任何 PR、任何决策都必须先读这一份。
> **当 ADR、Roadmap、代码与本文件冲突时，以本文件为准**——除非通过新版 Charter 修订程序覆盖。

---

## 1. 项目目标（What this project IS）

**Prompt2App** 是一个**个人作品集项目**，目标是在校招/实习面试中证明以下三件事：

1. **AI 应用工程能力**：能把 LLM 作为工程组件而不是玄学，体现在 Router / Agent / Tool / Eval 上。
2. **架构设计与决策能力**：对每个关键选择能讲清楚备选方案、决策依据、代价与复盘指标。
3. **工程化与治理能力**：让 AI 协作的项目仍然保持可读、可追溯、可演进。

**判定项目成功的硬指标**（Definition of Success）：
- [ ] 面试时能针对 Router / Agent / Eval / ADR 四个话题各展开 5 分钟。
- [ ] 简历上每一句描述都能回到 GitHub 仓库的具体文件 / commit / 数据。
- [ ] 6 个月后回看，仍能凭 ADR + Task Record 重建当时的决策上下文。

---

## 2. 核心能力（In Scope）

只投入资源到下列 4 + 1 个核心能力：

| 优先级 | 能力 | 价值定位 |
| --- | --- | --- |
| **P0** | **Prompt Eval 体系** | 工程化的尺子，所有改动的回归基准 |
| **P0** | **AI Router**（规则 + LLM 兜底两层） | 成本优化 + 系统设计 + 数据驱动 |
| **P0** | **Tool Calling Agent**（含安全网关） | Agent 工程实践 |
| **P0** | **ADR 决策记录** | 架构思考的可追溯产物 |
| **P1** | **Generation Metrics**（一表一 SQL） | 可观测性最小可行版 |

---

## 3. 明确不做（Out of Scope）

下列内容**已被评估并决定不做**。如果未来要做，必须先写新 ADR 推翻：

- ❌ **微服务化**（ADR-0001 已删 Dubbo / Nacos，3 服务规模无 RPC 必要）
- ❌ **LangGraph4j Workflow 主路径**（ADR-0007，单步 Agent 已能覆盖当前复杂度）
- ❌ **MinIO / OSS 对象存储**（ADR-0006，单机本地够用，预留接口）
- ❌ **JWT / Refresh Token 改造**（Cookie Session 在作品集场景够用）
- ❌ **PostgreSQL / pgvector 迁移**（无 RAG 需求）
- ❌ **React / Next.js 迁移**（与 AI 工程能力无关）
- ❌ **Docker per-app 沙箱**（路径白名单 + 调用次数熔断够用）
- ❌ **Grafana 深度看板**（一张 SQL 报表页够用）
- ❌ **Lighthouse / a11y / 视觉相似度评测**（镀金维度）
- ❌ **CDN / 多区域部署**

> **为什么写明"不做"比写明"做"更重要**：作品集项目最大的失败模式不是没做完，是**做了一堆 P3 杂事却没把 P0 做透**。每条"不做"都对应一个被克制的本能冲动。

---

## 4. 质量底线（Quality Bars）

| 维度 | 底线 |
| --- | --- |
| **评测回归** | 任何影响 AI 行为的改动，必须跑评测集；分数下降 > 5% 不允许合入 |
| **ADR 覆盖** | 任何新增/修改/废弃的架构选择，必须有 ADR；架构变更不允许"裸 commit" |
| **Task Record** | 任何持续 > 30 分钟的工作，必须留 Task Record |
| **Phase 边界** | 不允许跨 Phase 开发；当前 Phase 见 [`docs/roadmap/current-phase.md`](./docs/roadmap/current-phase.md) |
| **CI 红线** | Tool 安全测试（Phase 3 起）必须 100% 通过 |
| **配置外部化** | 所有部署变量（路径/host/端口/密钥/模型名/阈值）走 `Prompt2AppProperties` + `.env`；业务代码禁止硬编码（v1.1 / Phase 8 / ADR-0010）|

---

## 5. AI 协作原则（如何让 AI 不跑偏）

本项目大量代码由 AI 协作产出。为防止 AI 在多轮迭代中漂移，强制以下流程：

1. **AI 任何工作开始前，必须读取**：
   1. 本文件（`PROJECT_CHARTER.md`）
   2. [`docs/governance/ai-working-rules.md`](./docs/governance/ai-working-rules.md)
   3. [`docs/roadmap/current-phase.md`](./docs/roadmap/current-phase.md)
   4. 相关 ADR（如有）

2. **架构性变更必须先提案再执行**：见 [`docs/governance/architecture-change-proposal-template.md`](./docs/governance/architecture-change-proposal-template.md)

3. **文档先于代码**：设计文档 / ADR 没更新前，禁止动业务代码。

4. **每个任务结束必须留痕**：Task Record + 必要时更新 ADR / Roadmap。

---

## 6. 修订流程（如何变更本文件）

本文件**不允许直接修改**。修订流程：

1. 起草新版本 Charter，差异点单独列出。
2. 写一条 ADR，说明触发修订的事实变化与必要性。
3. ADR 状态置为 Accepted 后，再覆写本文件。
4. 在 Task Record 中留一条 `charter-revision-YYYYMMDD`。

---

## 7. 当前生效版本

- **版本**：v1.1
- **生效日期**：2026-06-18（Phase 0-6） + 2026-06-19（Phase 7 收尾、Phase 8 配置统一化）
- **v1.1 微补丁**（ADR-0010 触发）：§4 增加 "配置外部化" 质量底线
- **Phase 0-8 全部完成**：见 [`docs/roadmap/milestones.md`](./docs/roadmap/milestones.md)
- **下次例行回看**：项目目标从"作品集"切换到"对外服务"时，或满足下列任一条件：
  - 出现需要推翻 §3「明确不做」中任一条目的诉求
  - 团队规模 > 1 人，需要重新评估治理体系
  - 招聘场景需求变化（例如开始要求 SaaS 经验）
