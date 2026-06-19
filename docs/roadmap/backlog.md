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
- **2026-06-18** · `mvn compile -DskipTests` 兜底验证：~~在 Phase 1 启动前跑一次~~ ✅ 已在 Phase 0 evaluator 任务中跑通（JDK 21 + Lombok + 包重命名后），191 class 全部产出。
- **2026-06-18** · **JDK 版本锁定**：本机 brew 默认 JDK 25 与 Lombok 不兼容；当前依赖 JDK 21（`/opt/homebrew/Cellar/openjdk@21`）。Phase 1 应在 `pom.xml` 加 `<maven.compiler.release>21</maven.compiler.release>`，并在 README / docs/architecture/system-overview.md 写明本地开发要求。
- **2026-06-18** · **接入真 `AgentInvoker`**：当前 evaluator 走 stub。Phase 1 模块化重组完成、Phase 2 删 langchain4j patch 之后，写一个 `DirectServiceAgentInvoker` 直接注入 `AiCodeGeneratorService`，让 `mvn test -Dtest=EvalRunnerSmokeTest` 默认仍走 stub，但提供 `EvalRunnerLiveTest`（@DisabledIfEnvironmentVariable）跑真 LLM。

## Phase 2 范围

- **2026-06-18** · 24 个 SpringBootTest 集成测试需环境才能跑（DB / Redis / API key）。`langgraph4j` 旧测试 + `WebScreenshotUtilsTest` + `AiCodeGenTypeRoutingServiceTest` 全部依赖完整 Spring 上下文。Phase 2/3 时考虑：① 引入 testcontainers 提供本地基础设施 ② 给这些测试加 `@DisabledIfEnvironmentVariable(named = "CI", matches = "true")` 仅本地手动跑 ③ 拆成纯单测 + 集成测试两套。

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
- **2026-06-18** · README.md 改写——目前是 Prompt2App 简介版本，结尾时改写为"面向招聘官的项目介绍"，含 6 Phase 演进图 + 4 个面试爆点。
- **2026-06-19** · `GenerationMetricService.completeOutcome` 接入：当前 Phase 6 只接了 `recordRouting`，generation 阶段的 `completeOutcome`（含 token / cost / tool_call_count / success）需要改 `AiCodeGeneratorFacade` SSE 流的 onComplete / onError 钩子，跨多个文件。Phase 7 README 改写时若有时间再补；不影响 Phase 6 数据结构 + 报表 API 的完整性。

---

## 跨 Phase / 待评估

- **2026-06-18** · `yu-ai-code-mother-frontend/` 的 ADR：是否在 Phase 1 一并评估前端的目录结构？暂记，等 Phase 1 启动时决定。
- **2026-06-18** · `grafana/` 与 `prometheus.yml` 是否删除：与 Charter §3「不上 Grafana」一致，但不是当前 Phase 工作；Phase 7 收尾时一并清理。

---

## 已驳回（Rejected）

> 写到这里的想法表示**已评估并明确不做**，配套 ADR 或显式理由。

- _（暂无）_
