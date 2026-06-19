# ADR-0007：不引入 LangGraph4j Workflow 作为主路径

- **状态**：Accepted
- **日期**：2026-06-19
- **决策者**：项目作者
- **相关 Phase**：Phase 7 · 收尾
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

教学版项目引入了 LangGraph4j（Java 移植的 LangGraph）作为"高级工作流编排"的实验子模块：

```
src/main/java/com/prompt2app/agent/workflow/   ← 原 langgraph4j/，Phase 1 重命名
├── ai/
├── config/
├── demo/
├── model/
├── node/
├── state/
└── tools/
```

主要包含一个 "Plan → Execute → Review" 三节点工作流的 demo，用于复杂多页站点生成。

**事实判断**（基于 6 周深度使用）：

- **现有单步 Tool Calling Agent 已能覆盖 95% 场景**：HTML / MultiFile / Vue 三种策略，单步 Agent 各自工作良好
- **Workflow 仅在 Vue 复杂应用场景边际有用**：但即使在那里，多轮 Tool Calling + AI 自主修正已经足够
- **Workflow 引入认知开销**：StateGraph + Node + Edge 概念对于"读代码的人"额外学习曲线
- **教学版的 demo 节点目前没在主链路调用**：只在测试代码 / RouterNode 实验路径中触发
- **维护成本不对称**：保留 demo 代码 ≠ 0 维护——每次 LangChain4j 升级都要看 LangGraph4j 子模块还能不能跑

PROJECT_CHARTER §3「明确不做」第 2 项把这个写在墙上：

> ❌ **LangGraph4j Workflow 主路径**（ADR-0007 后续评估）

ADR-0001 §不做的事 也明确："命运待 ADR-0007"。本 ADR 落地这个决策。

---

## 备选方案（Options）

### 方案 A：完全删除 `agent/workflow/`

- 优点：
  - 仓库更干净
  - 减少招聘官读代码的疑惑（"这个 workflow 在哪用？"）
  - 1500+ 行 Java 代码减重
- 缺点：
  - 失去"我评估过 LangGraph4j"的实证
  - 未来想用时要重新引入
  - 历史代码已经 Phase 1 重命名整合，删除等于浪费 Phase 1 工作

### 方案 B：保留代码 + 写 ADR 说明"评估过、不做主路径、保留作实验"（**本决策**）

- 优点：
  - **诚实**：实证我做了评估，不是回避
  - **加分项**："我用过 X 但决定不用，并写了 ADR" 比"我没听说过 X"强 N 倍
  - 未来如果发现复杂场景确实需要工作流，代码现成
  - 与 Charter §3 一致
- 缺点：
  - 维护成本（升级 LangChain4j 时跟着升级 LangGraph4j）
  - 招聘官可能问"这部分代码做什么的"——但这正好是讲故事的机会

### 方案 C：保留 + 在主链路启用一个 demo 路径（混合）

- 优点：保留"会用"的展示
- 缺点：
  - 与"主线只走单步 Agent"决策矛盾
  - 引入"什么时候走 workflow 什么时候走 Agent"的额外路由判断
  - 增加评测集复杂度
- 长期成本：高

---

## 决策（Decision）

**选择方案 B**：保留 `agent/workflow/` 作为实验代码，**不在主链路调用**。

### 关键约束

- 主链路（`AppServiceImpl.createApp`）走 `RoutingService`（规则 + LLM 兜底两层）→ 直接选三策略
- `RouterNode.java`（workflow 节点）保留作为 LangGraph4j demo 入口，但不在生产路径触发
- 维护方式：被动维护——LangChain4j 升级时如果 LangGraph4j 子模块编译失败，临时降级或注释

### Workflow 评估结果对照

| 方案要求 | LangGraph4j 表现 | 单步 Tool Calling Agent 表现 |
| --- | --- | --- |
| 简单 HTML | 杀鸡用牛刀 | ✅ 一次生成 |
| MultiFile 多页 | 能用，但每页一个 Node 太重 | ✅ 一次生成多文件 |
| Vue 复杂应用 | 理论上 Plan-Execute-Review 优秀 | ✅ 多轮 Tool Calling 已能覆盖 |
| 错误恢复 | Workflow 节点重试 | ✅ Tool 错误反馈给 LLM 自纠 |
| 调试 | 状态机日志，可视化困难 | ✅ Tool 调用日志清晰 |

**结论**：在所有维度上，单步 Tool Calling Agent 都至少持平 Workflow，且简单许多。

---

## 代价（Consequences）

### 正面

- **诚实记录**：未来招聘官追问"为什么不用 LangGraph 这种现代框架"，可以正面回答："评估过，写了 ADR-0007，结论是 Tool Calling 已够"
- **故事性**："我用了一个新技术、跑了 demo、决定不在生产用"是高级工程师才有的判断力
- **可复用资产**：未来真有 multi-step agent 需求，代码可激活
- **与 ADR-0001、ADR-0006 一脉相承**：都是"作品集场景下的最小可行架构"决策

### 负面

- **维护成本**：LangChain4j 1.5.1 → 未来升级时如 LangGraph4j 跟不上，需要锁定版本或注释代码
- **代码体积**：~1500 行 Java 不参与主链路，仍占编译时间
- **首次读代码的人会疑惑**：但 ADR-0007 + README 链接能解决

### 中性

- 评测集（25 case）从来不走 workflow 路径，本决策不影响评测集
- Tool 安全（Phase 3）三层防御对 workflow 路径同样适用——如果未来激活，安全已就位

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估本决策：

- 实测发现 Vue 复杂应用场景下单步 Tool Calling 失败率 > 30%
- 评测集中出现"非线性流程"需求（例如"先做线框图、确认后再实现"）
- LangGraph4j 升级到稳定 1.0+ 版本，API 大改值得重新关注
- 团队规模 > 1 人，需要更结构化的 Agent 编排表达

---

## 关于"代码不删但不在主链路"的元规则

> "保留死代码"在生产项目里是反模式，但在作品集项目里是**叙事工具**。

死代码的代价：
- ❌ 维护负担（LangChain4j 升级跟着改）
- ❌ 编译时间
- ❌ 读代码者的认知负担

死代码的价值：
- ✅ 实证你评估过，而非回避
- ✅ 可激活的"未来选项"
- ✅ 招聘官的钩子（"这部分做什么的？" → 你讲 5 分钟评估过程）

**本作品集项目的边界**：

- 删除：低价值、易引人误解的代码（教学版 microservice/ → ADR-0001 删了）
- 保留：评估过、ADR 论证过、未来可激活的代码（langgraph4j/ → 本 ADR 决策保留）

---

## 参考资料（References）

- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §3 「明确不做」第 2 项
- [`docs/adr/0001-modular-monolith.md`](./0001-modular-monolith.md) 「命运待 ADR-0007 处理」
- 现有代码：`src/main/java/com/prompt2app/agent/workflow/`
- 同一脉络的 ADR：[ADR-0001](./0001-modular-monolith.md)（删 microservice）vs [ADR-0007 本文](./0007-no-langgraph4j-workflow.md)（保留 workflow）的对比
- 业界参考：[LangGraph 文档](https://langchain-ai.github.io/langgraph/) + [LangGraph4j Java 移植](https://github.com/bsorrentino/langgraph4j)
