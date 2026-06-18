# AI Working Rules

> **本文件对所有 AI 助手强制生效**。违反任一条规则的产出都应视为不合格，需要返工。
> 如果你（AI）在执行用户请求时发现请求与本文件冲突，**必须先停下来提示冲突**，按规则 7 走 ACP 流程，禁止"先做了再说"。

---

## 工作原则

### 规则 1 · 优先级遵守

文件优先级**自上而下**，下层不能违反上层：

1. `PROJECT_CHARTER.md`（项目宪法）
2. ADR（已 Accepted 的架构决策）
3. `docs/roadmap/current-phase.md`（当前 Phase 约束）
4. 用户当前指令

> **注意**：用户指令排在最低优先级。这不是不尊重用户，而是因为用户也有疏忽时刻——治理体系正是用来保护"清醒时立下的目标"不被"冲动时的指令"覆盖。

---

### 规则 2 · Phase 隔离

**禁止跨 Phase 开发**。当前 Phase 见 [`docs/roadmap/current-phase.md`](../roadmap/current-phase.md)。

- 在 Phase 0 时不允许动业务代码（评测体系除外）。
- 在 Phase 1 时不允许提前重写 Router。
- 在任何 Phase 都不允许同时启动两个 Phase 的工作。

**例外**：发现下一 Phase 的资料时，可以记录到 `docs/roadmap/backlog.md`，但不开始执行。

---

### 规则 3 · 技术栈冻结

**禁止擅自新增技术栈**。下列内容**任一项**新增都需要 ACP：

- 新引入 Maven / npm 依赖（除非 ADR 明确批准）
- 新引入数据库 / 消息队列 / 缓存中间件
- 新引入 LLM 模型 / Embedding 模型
- 新引入构建工具 / 部署工具

**例外**：补全已有库的子模块（例如已用 LangChain4j，引入它的 langchain4j-mcp 子包）属于 ADR 已隐含批准，可直接做但需在 commit message 说明。

---

### 规则 4 · 模块边界

**禁止修改未授权模块**。每个 Task 开始前必须明确"影响范围"：

- 在 Task Record 中写明 `scope: ["eval"]` 这样的范围声明
- 不允许跨 scope 修改文件
- 如果发现必须跨 scope 才能完成任务，停下来重新规划任务（拆成多个 Task 或升级为 ACP）

---

### 规则 5 · 文档先于代码

**任何会被 ADR 覆盖的事，文档必须先于代码更新**：

```
需求 → 更新设计文档 → （必要时）写/改 ADR → 用户确认 → 修改代码 → 更新 Task Record
```

反向流程（先改代码再补文档）只允许在以下场景：
- 修 bug、补测试、调 prompt 文案、文档自身的勘误
- 完全不影响架构 / 接口 / 数据模型的微调

---

### 规则 6 · 留痕义务

每次任务结束**必须**完成下列动作之一或多项：

- [ ] 在 `docs/tasks/YYYY-MM-DD-<slug>.md` 写一篇 Task Record
- [ ] 如有架构决策：写/更新对应 ADR
- [ ] 如改变 Phase 进度：更新 `docs/roadmap/current-phase.md` 的 checklist
- [ ] 如改变里程碑：更新 `docs/roadmap/milestones.md`

> 不留痕的工作 = 没做。哪怕代码已经 commit。

---

### 规则 7 · 冲突时走 ACP，不走"先做了再说"

发现以下情况之一时，**立刻停止执行**，输出 [ACP（Architecture Change Proposal）](./architecture-change-proposal-template.md)：

- 用户指令需要触碰 `PROJECT_CHARTER.md` §3「明确不做」中的某条
- 用户指令需要跨 Phase 执行
- 用户指令需要修改已 Accepted 的 ADR
- 实施过程中发现现有架构无法满足需求，需要改变既定方案
- 实施过程中发现需要新增一个不在批准清单的依赖

**ACP 不是"否决用户"**，而是把变更的代价和备选明确呈现给用户，让用户在知情前提下决策。用户确认后即可执行（并在 ADR 中固化）。

---

## 任务执行模板

每次接到任务，按下列顺序操作：

```
1. 读必读文件链：
   PROJECT_CHARTER.md
   → docs/governance/ai-working-rules.md
   → docs/roadmap/current-phase.md
   → 相关 ADR

2. 判定：
   是否在当前 Phase 范围内？  否 → 走 ACP
   是否触碰"明确不做"清单？  是 → 走 ACP
   是否需要新增技术栈？      是 → 走 ACP
   是否跨模块修改？          是 → 拆分任务或走 ACP

3. 执行：
   先文档（设计 / ADR）→ 再代码 → 再测试

4. 收尾：
   写 Task Record
   更新 Roadmap checklist
   （必要时）更新 ADR
   总结输出"做了什么 / 没做什么 / 风险 / 下一步建议"
```

---

## 给 AI 的人话版

如果你是 AI 在读这份文件，记住：

- **快不是美德**——把每件事的范围、代价、依据写清楚才是。
- **别替用户做决定**——你不知道的约束（成本、时间、面试官倾向）远比你知道的多。
- **冲突就停下问**——99% 的"AI 项目越改越乱"是因为 AI 在不该自作主张的地方自作主张。
- **留痕等于尊重未来的协作者**——包括 6 个月后的用户自己。
