# Prompt2App

> **AI 网页生成平台 · 个人作品集项目**
>
> 用户输入需求描述，AI 自动选择策略（HTML / MultiFile / Vue）生成可运行网页。
> 支持流式输出、Tool Calling Agent、可视化编辑、CI 自动评测回归。

[![CI](https://github.com/pppisnew/Prompt2App/actions/workflows/eval.yml/badge.svg)](https://github.com/pppisnew/Prompt2App/actions/workflows/eval.yml)
&nbsp;[![ADR](https://img.shields.io/badge/ADR-9_records-blue)](./docs/adr/)
&nbsp;[![Tests](https://img.shields.io/badge/tests-94%2F95-success)](./.github/workflows/eval.yml)
&nbsp;[![Phase](https://img.shields.io/badge/phase-7%2F7%20done-success)](./docs/roadmap/milestones.md)

---

## 60 秒看完本项目

这是一个**AI 应用工程**作品集项目，把一个教学版 demo（Spring Boot + LangChain4j）逐步重构为：

- **设计了 4 个面试可独立展开 5 分钟的核心命题**（见下文）
- **写了 9 篇 ADR**（架构决策记录），每个关键选择都有可追溯的论证 + 备选方案 + 复盘指标
- **94 个单元测试**进 CI（GitHub Actions），含路由准确率回归 / Tool 安全攻击用例 / 评测框架自身
- **不堆砌技术栈**：明确写下 9 项「不做」并用 ADR 论证，专注做透 4 个核心能力

读完 README 后建议按这个顺序看：[`PROJECT_CHARTER.md`](./PROJECT_CHARTER.md) → [`docs/adr/`](./docs/adr/) → [`docs/architecture/`](./docs/architecture/) → 代码。

---

## 4 个核心能力（面试爆点）

### ⭐ 1. AI Router · 规则 + LLM 兜底两层架构

> [`com.prompt2app.router`](./src/main/java/com/prompt2app/router/) · [ADR-0003](./docs/adr/0003-ai-router-two-layer.md) · [设计文档](./docs/architecture/router-design.md)

```
userPrompt
   │
   ├─► [Layer 1] RuleRouter (毫秒级)
   │     ├─ MULTI_FILE 关键词 + N 页正则
   │     ├─ VUE 强关键词 (vue/应用/dashboard/番茄钟/...)
   │     └─ HTML 短小判定
   │
   └─► [Layer 2] LLM Fallback
         success → RoutingDecision(LLM_FALLBACK, conf=0.6)
         error   → RoutingDecision(LLM_ERROR_FALLBACK, HTML, conf=0.3)
```

- **9 成请求毫秒级走规则**，长尾走 LLM 兜底，LLM 异常降级到 HTML（最便宜策略）
- 每次决策落 `RoutingDecision { strategy, layer, reason, confidence, durationMs }`，Phase 6 直接入 metric 表
- 25 case 评测集 3 轮规则调优：**60% → 64% → 80% 准确率**（命中率 88%）
- CI 含 `RouterAccuracyTest` 阈值守护（hit ≥ 80%、accuracy ≥ 60%）

### ⭐ 2. Tool Calling Agent · 三层安全防御

> [`com.prompt2app.agent.tools.safety`](./src/main/java/com/prompt2app/agent/tools/safety/) · [ADR-0004](./docs/adr/0004-tool-safety.md) · [设计文档](./docs/architecture/agent-design.md)

| 层 | 类 | 拒绝原因 |
| --- | --- | --- |
| **Layer 1 · Schema 校验** | `PathValidator` | `..` / 绝对路径 / null byte / 控制字符 |
| **Layer 2 · 能力沙箱** | `Sandbox` | canonical 路径越权 / 软链接逃逸 / 关键文件保护 |
| **Layer 3 · 行为熔断** | `ToolCallCounter` | 单会话调用超限 / 单文件高频修改 |

- **5 个真实漏洞 closed**：路径穿越 / 绝对路径 bypass / 关键文件按文件名匹配 / 死循环 / 软链接逃逸
- **27 单测进 CI**：JUnit 5 含 POSIX 软链接 conditional test + 10 线程并发安全
- 业务调用方零额外代码：`sandbox.resolveForXxx(path)` + `counter.recordCall(...)` 两行接入

### ⭐ 3. Prompt Eval · 三维评分自动化

> [`com.prompt2app.eval`](./src/main/java/com/prompt2app/eval/) · [ADR-0005](./docs/adr/0005-evaluation-automation.md) · [设计文档](./docs/architecture/eval-design.md)

```
case → AgentInvoker → CompositeScorer:
                       ├─ RubricScorer    must_contain / must_not_contain / minFiles
                       ├─ RenderScorer    HTML body 非空 / Vue 工程结构（无 Playwright）
                       └─ LlmJudgeScorer  LangChain4j AiService + 5 档语义锚点 + JSON 解析容错
                                      ↓
                                 CaseFinalScore (否决合分)
                                      ↓
                              DiffReporter vs prev baseline
                                      ↓
                              eval/reports/<sha>.md
```

- **25 case 评测集**进 CI 自动回归
- **三维评分**：客观（rubric + render） + 主观（LLM-as-Judge）；"否决项严苛、主分柔软"
- **Diff 回归**：单 case Δ > 10 标 regressed；总平均 Δ > 5 全局红灯
- LLM 异常时 fallback 50 分**不 veto**——避免 LLM 抽风导致 CI 错误红灯
- 33 单测覆盖（Render / Composite / Diff / LLM 解析）

### ⭐ 4. ADR · 架构决策记录

> [`docs/adr/`](./docs/adr/) · 9 篇 Accepted

每个关键架构选择都有 ADR：**背景 / 备选方案（≥2）/ 决策 / 代价 / 复盘指标**。这是面试时被问"为什么这么设计"的弹药库。

| # | 标题 | Phase |
| --- | --- | --- |
| [0001](./docs/adr/0001-modular-monolith.md) | 从微服务回退到模块化单体 | 1 |
| [0002](./docs/adr/0002-remove-langchain4j-patch.md) | 升级 LangChain4j 1.1 → 1.5 + 删源码覆盖 patch | 2 |
| [0003](./docs/adr/0003-ai-router-two-layer.md) | AI Router 规则 + LLM 兜底两层架构 | 4 |
| [0004](./docs/adr/0004-tool-safety.md) | Tool Calling 三层安全防御 | 3 |
| [0005](./docs/adr/0005-evaluation-automation.md) | 评测体系自动化（三维评分 + diff + CI） | 5 |
| [0006](./docs/adr/0006-no-minio.md) | 当前阶段不引入 MinIO/OSS | 7 |
| [0007](./docs/adr/0007-no-langgraph4j-workflow.md) | 不引入 LangGraph4j Workflow 作主路径 | 7 |
| [0008](./docs/adr/0008-evaluation-first.md) | 重构开始前先建立评测集 | 0 |
| [0009](./docs/adr/0009-project-rename-to-prompt2app.md) | 项目重命名为 Prompt2App（越界批准） | 0 |

---

## 项目演进时间线

```
教学版 (microservice-final tag, 893918c)
   │
   ▼
Phase 0  评测基线 + 治理体系 + Evaluator 骨架
Phase 1  模块化单体收敛（删 microservice，6 包按域分）+ ADR-0001
Phase 2  删 LangChain4j 源码覆盖 patch + 升级 1.5.1 + ADR-0002
Phase 3  Tool 三层安全 + 27 单测 ⭐⭐⭐⭐⭐ #1
Phase 4  AI Router 两层 + 17 单测 + 80% 准确率 ⭐⭐⭐⭐⭐ #2
Phase 5  Eval 三维评分 + Diff + CI 三大门控 + 33 单测 ⭐⭐⭐⭐⭐ #3
Phase 6  generation_metric 表 + 4 类 SQL 报表
Phase 7  ADR-0006/0007 收尾 + README 改写（你正在读这里）
```

详见 [`docs/roadmap/milestones.md`](./docs/roadmap/milestones.md) 完整里程碑表。

---

## 仓库导览

```
PROJECT_CHARTER.md          ← 项目宪法（目标 / 范围 / 9 项「不做」）
AGENTS.md                   ← AI 助手协作入口（agents.md 规范）

docs/
  governance/               ← 协作规则、ACP 模板、DoD
  adr/                      ← 9 篇架构决策记录
  roadmap/                  ← 里程碑 / 当前 phase / backlog
  tasks/                    ← 11 篇工作留痕
  architecture/             ← 6 篇设计文档（system / module / router / agent / eval / metric）
  reverse-engineering/      ← 教学版项目分析（Phase -1 资产）

eval/
  schema/case.schema.yaml   ← 评测 case 字段定义
  cases/*.yaml              ← 25 个评测用例
  reports/baseline.md       ← stub mode baseline（CI artifact）

src/main/java/com/prompt2app/
  app/                      ← 业务领域（controller/service/mapper/model）
  router/                   ← AI Router（Phase 4 ⭐ #2）
  agent/                    ← Agent + Tool + Codegen + Workflow（Phase 3 ⭐ #1）
  eval/                     ← 评测框架（Phase 5 ⭐ #3）
  metric/                   ← 生成质量埋点（Phase 6）
  infra/                    ← 横向基础设施

.github/workflows/eval.yml  ← CI：eval / router / safety 三大门控
sql/generation_metric.sql   ← Phase 6 表结构
```

---

## 技术栈

- **后端**：Spring Boot 3 / **LangChain4j 1.5.1** / MyBatis-Flex / Redis / MySQL
- **前端**：Vue 3 / Vite / Ant Design Vue（前端目录待 Phase 8+ 重命名）
- **AI 模型**：DeepSeek（主） + 路由层小模型 + LLM-as-Judge
- **构建/CI**：JDK 21（Lombok 兼容）+ Maven + GitHub Actions
- **测试**：JUnit 5 + Mockito（94 单测，CI 自动跑）

---

## 数据：项目体量速览

| 维度 | 数字 |
| --- | --- |
| 完成 Phase | 7 / 7 |
| ADR | 9 篇 Accepted |
| Task Records | 11 篇 |
| 单元测试 | 94 个（CI 守护） |
| 主代码行数（业务/治理） | ~6500 行 |
| 删除的教学版代码 | ~11000 行（microservice + langchain4j patch） |
| Git 提交（feature 分支） | 12 个原子 commit |

---

## 快速运行（本地）

```bash
# 1. 启动 MySQL + Redis（自备或 docker compose）
# 2. 创建数据库 + 导入表
mysql -u root -p < sql/create_table.sql
mysql -u root -p < sql/generation_metric.sql

# 3. 配置 application-local.yml（复制 application.yml 并填 DeepSeek API Key）

# 4. 启动后端
JAVA_HOME=/path/to/jdk21 mvn spring-boot:run

# 5. 跑测试（不依赖 DB）
mvn test -Dtest='com.prompt2app.eval.*Test,com.prompt2app.router.*Test,com.prompt2app.agent.tools.safety.*Test,com.prompt2app.metric.*Test'

# 6. 看评测报告
cat eval/reports/baseline.md
```

> **JDK 21 是硬要求**——Lombok 与 JDK 25+ 不兼容。详见 [Phase 0 task record](./docs/tasks/2026-06-18-phase0-evaluator-and-baseline.md)。

---

## 简历段落（直接抄）

> **Prompt2App · AI 网页生成平台**（个人作品集，[github.com/pppisnew/Prompt2App](https://github.com/pppisnew/Prompt2App)）
>
> 基于 Spring Boot 3 + LangChain4j 1.5.1 构建，从教学微服务 demo 重构为模块化单体 AI 工程项目。
> 设计 **AI Router 规则 + LLM 兜底两层路由**（25 case 评测集 80% 准确率 / 88% 命中率），
> **Tool Calling Agent 三层安全防御**（27 单元测试覆盖路径穿越 / 软链接逃逸 / 调用熔断），
> **三维 Prompt 评测体系**（编译 / 渲染 / LLM-as-Judge + Diff 回归 + CI 自动门控），
> **生成质量指标埋点**（15 字段 metric 表 + 4 类聚合 SQL 报表）。
>
> 通过 9 篇 ADR 记录所有关键架构决策（含「不做微服务 / 不做 Workflow / 不做 OSS」的反向论证）。
> 94 单元测试进 GitHub Actions CI 三大门控（评测框架 / 路由准确率 / Tool 安全），守护后续每次改动。

---

## 致谢

本项目骨架来自 [程序员鱼皮 - yu-ai-code-mother 教学项目](https://github.com/liyupi/yu-ai-code-mother)，是非常优秀的 AI 全栈学习资源。
本仓库在此基础上做的是**个人化的工程治理重构**——从单纯实现功能转向"如何让 AI 协作的项目仍然可读、可追溯、可演进"。

教学版完整状态保留在 git tag [`microservice-final`](https://github.com/pppisnew/Prompt2App/tree/microservice-final)，可对比演化。

---

## 开发者协议

- 任何 AI 助手在本仓库工作前必须读 [`AGENTS.md`](./AGENTS.md) 和 [`docs/governance/ai-working-rules.md`](./docs/governance/ai-working-rules.md)
- 架构性变更必走 [ACP（Architecture Change Proposal）](./docs/governance/architecture-change-proposal-template.md)
- ADR 不可变，新决策覆盖旧决策时新增 ADR 而非修改

---

## License

参见 [`LICENSE`](./LICENSE)（如有）。
