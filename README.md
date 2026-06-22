# Prompt2App

> **AI 网页生成平台 · 个人作品集项目**
>
> 用户输入需求描述，AI 自动选择策略（HTML / MultiFile / Vue）生成可运行网页。
> 支持流式输出、Tool Calling Agent、可视化编辑、**3 轮均分评测体系**、CI 守护。

[![ADR](https://img.shields.io/badge/ADR-13_records-blue)](./docs/adr/)
&nbsp;[![Tasks](https://img.shields.io/badge/Task_Records-20-blue)](./docs/tasks/)
&nbsp;[![Eval](https://img.shields.io/badge/Eval-25_cases_×_3_rounds-success)](./eval/reports/baseline-real.md)
&nbsp;[![Phase](https://img.shields.io/badge/v1.1_locked_+_Eval_增量-success)](./docs/roadmap/current-phase.md)

> CI badge 待接入（P1-2 backlog，详见 [docs/roadmap/current-phase.md](./docs/roadmap/current-phase.md)）

---

## 60 秒看完本项目

这是一个**AI 应用工程**作品集项目，把一个教学版 demo（Spring Boot + LangChain4j）重构为：

- **5 个面试可独立展开 5 分钟的核心命题**（见下文）
- **13 篇 ADR**（架构决策记录），每个关键选择都有可追溯的论证 + 备选方案 + 复盘指标——包括 **3 篇明确写「不做什么」的反向 ADR**
- **20 篇 Task Records**记录每个 >30 分钟的工作，**含 2 次治理违规复盘**——诚实记录代码以外的事实
- **真实 LLM 三轮均分评测体系**：25 case × 3 轮取均值±标准差，治理 LLM 抽样波动（单跑曾出现 ±20 分波动）
- **不堆砌技术栈**：明确写下 9 项「不做」并用 ADR 论证，专注做透 5 个核心能力

读完 README 后建议按这个顺序看：[`PROJECT_CHARTER.md`](./PROJECT_CHARTER.md) → [`docs/adr/`](./docs/adr/) → [`docs/architecture/`](./docs/architecture/) → 代码。

---

## 给面试官的 30 秒 Pitch

> **问题**：现在 LLM 代码生成 demo 满天飞，但绝大多数项目**没有真实评测**——很难回答"这版改完真的更好了吗"。
>
> **做法**：建了一套 25 case × 3 轮的真实 LLM 评测体系，三维评分（确定性 Rubric / 渲染 Render / LLM-as-Judge），单 case 失败可断点续跑，结果以"均分 ± 标准差"呈现。
>
> **难点故事**（任选一个 5 分钟）：① **VUE Render 评分器与产物读取互斥**导致 10/10 VUE case 全 0 分的根因排查；② 一次评测跑完总分倒退 20 分，**逐 case 实测**确证回归源是 LLM 抽样而非代码，进而做了多轮均分治理。
>
> **量化**：3 轮均分 `40.83 ± 7.52` 分 / VUE 维度 0 → `0`（已识别 bug：readMergedOutput 读错文件，方案 D 待修） / 13 ADR / 20 Task Records / 32 测试类、56 eval 测试方法。
>
> **诚实**：项目里明确记录了 2 次治理违规事件 + 1 次擅自偏离 Task Record 设计的事实——比"完美履历"更说明真实工程现场。

---

## 5 个核心能力（面试爆点）

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
- 每次决策落 `RoutingDecision { strategy, layer, reason, confidence, durationMs }`，入 `generation_metric` 表
- 25 case 评测集 3 轮规则调优：**60% → 64% → 80% 准确率**（命中率 88%）

### ⭐ 2. Tool Calling Agent · 三层安全防御

> [`com.prompt2app.agent.tools.safety`](./src/main/java/com/prompt2app/agent/tools/safety/) · [ADR-0004](./docs/adr/0004-tool-safety.md) · [设计文档](./docs/architecture/agent-design.md)

| 层 | 类 | 拒绝原因 |
| --- | --- | --- |
| **Layer 1 · Schema 校验** | `PathValidator` | `..` / 绝对路径 / null byte / 控制字符 |
| **Layer 2 · 能力沙箱** | `Sandbox` | canonical 路径越权 / 软链接逃逸 / 关键文件保护 |
| **Layer 3 · 行为熔断** | `ToolCallCounter` | 单会话调用超限 / 单文件高频修改 |

- **5 个真实漏洞 closed**：路径穿越 / 绝对路径 bypass / 关键文件按文件名匹配 / 死循环 / 软链接逃逸
- **27 单测**：JUnit 5 含 POSIX 软链接 conditional test + 10 线程并发安全
- 业务调用方零额外代码：`sandbox.resolveForXxx(path)` + `counter.recordCall(...)` 两行接入

### ⭐ 3. Prompt Eval · 三维评分自动化

> [`com.prompt2app.eval`](./src/main/java/com/prompt2app/eval/) · [ADR-0005](./docs/adr/0005-evaluation-automation.md) · [设计文档](./docs/architecture/eval-design.md)

```
case → AgentInvoker → CompositeScorer:
                       ├─ RubricScorer    must_contain / must_not_contain / minFiles
                       ├─ RenderScorer    HTML body 文本 ≥ 100 / Vue 工程检查 dist/index.html
                       └─ LlmJudgeScorer  LangChain4j AiService + 5 档语义锚点 + JSON 解析容错
                                      ↓
                                 CaseFinalScore (否决合分)
                                      ↓
                              eval/reports/baseline-real.md
```

- **25 case 评测集** · 三维评分：客观（rubric + render） + 主观（LLM-as-Judge）
- **"否决项严苛、主分柔软"**：rubric/render 做硬门控，LLM-Judge 做软评分
- LLM 异常时 fallback 50 分**不 veto**——避免 LLM 抽风导致 CI 错误红灯
- **56 单测覆盖**（含 RoundAggregator / RoundResult 持久化 / MultiRoundRunner 断点续跑）

### ⭐ 4. Eval 稳定性治理 · 3 轮均分 + temperature=0 + 断点续跑

> [`MultiRoundEvalRunner`](./src/main/java/com/prompt2app/eval/MultiRoundEvalRunner.java) · [ADR-0013](./docs/adr/0013-eval-multi-round-determinism.md) · [Task 2026-06-22](./docs/tasks/2026-06-22-eval-multi-round-determinism.md)

```
Round 1 ──┐
Round 2 ──┼──► JSON 中间报告 (机器读，断点续跑)
Round 3 ──┘    Markdown 单轮报告 (人读，单轮排查)
              ↓
         RoundAggregator → 均分 ± 标准差
              ↓
        baseline-real.md  (v2 多轮聚合)
```

- **触发原因**：上一轮代码改动跑完总分 55→35，**逐 case 实测确证是 LLM 抽样波动**而非代码回归，Charter §4 "评测回归 >5%" 红线被 LLM 噪声触发，治理成本不可持续
- **解决方案**：3 轮跑 + `temperature=0` 强制覆盖（`@TestPropertySource` 只在评测路径生效、不污染生产 0.7）+ 断点续跑（单轮失败可独立补跑）
- **报告格式**：每 case 三轮分数矩阵 + 均分 + 标准差；策略子均分；总分 ± 标准差
- **当前结果**：`3 轮均分 40.83 ± 7.52 / 100`（[最新 baseline](./eval/reports/baseline-real.md)）—— HTML 80.18±29.65 / MULTI_FILE 57.44±21.77 / VUE 0.00±0.00（VUE 维度受 readMergedOutput bug 影响为 0，[方案 D 待修](./docs/tasks/2026-06-22-eval-multi-round-determinism.md#附round-1-实证数据--p0-2-重新定位2026-06-22-1314-评测中诊断)）
- **关键设计**：用 appId 高位编码 round（Round N 的 appId 从 N×1,000,000+ 起）实现产物隔离，**零侵入** 5 处 `codeOutputDir` 调用方
- **代价**：单次评测 3× token + ~3h；但 Charter §4 红线重新可信

### ⭐ 5. ADR · 架构决策记录

> [`docs/adr/`](./docs/adr/) · **13 篇 Accepted**

每个关键架构选择都有 ADR：**背景 / 备选方案（≥2）/ 决策 / 代价 / 复盘指标**。这是面试时被问"为什么这么设计"的弹药库。

| # | 标题 | 类型 |
| --- | --- | --- |
| [0001](./docs/adr/0001-modular-monolith.md) | 从微服务回退到模块化单体 | 收敛 |
| [0002](./docs/adr/0002-remove-langchain4j-patch.md) | 升级 LangChain4j 1.1 → 1.5 + 删源码覆盖 patch | 升级 |
| [0003](./docs/adr/0003-ai-router-two-layer.md) | AI Router 规则 + LLM 兜底两层架构 | 设计 |
| [0004](./docs/adr/0004-tool-safety.md) | Tool Calling 三层安全防御 | 设计 |
| [0005](./docs/adr/0005-evaluation-automation.md) | 评测体系自动化（三维评分 + diff + CI） | 设计 |
| [0006](./docs/adr/0006-no-minio.md) | 当前阶段**不**引入 MinIO/OSS | **反向** |
| [0007](./docs/adr/0007-no-langgraph4j-workflow.md) | **不**引入 LangGraph4j Workflow 作主路径 | **反向** |
| [0008](./docs/adr/0008-evaluation-first.md) | 重构开始前先建立评测集 | 治理 |
| [0009](./docs/adr/0009-project-rename-to-prompt2app.md) | 项目重命名为 Prompt2App | 治理 |
| [0010](./docs/adr/0010-config-unification.md) | 配置统一化（.env + Spring profiles + Properties） | 治理 |
| [0011](./docs/adr/0011-drop-redis-chat-memory-store.md) | 删除 RedisChatMemoryStore | 收敛 |
| [0012](./docs/adr/0012-static-resource-dual-dir-and-url-unification.md) | 静态资源双目录 fallback + 前端 URL 统一 | 修复 |
| [0013](./docs/adr/0013-eval-multi-round-determinism.md) | **评测改为多轮均分 + temperature=0 + 断点续跑** | 设计 |

> **反向 ADR 是看点**：写"不做什么"比写"做什么"更需要论证能力——0006（不做 OSS）、0007（不做 Workflow）、PROJECT_CHARTER §3（9 条不做清单）。

---

## 项目演进时间线

```
教学版 (microservice-final tag, 893918c)
   │
   ▼
Phase 0  评测基线 + 治理体系 + Evaluator 骨架（评测先行 ADR-0008）
Phase 1  模块化单体收敛（删 microservice，6 包按域分）
Phase 2  删 LangChain4j 源码覆盖 patch + 升级 1.5.1
Phase 3  Tool 三层安全 ⭐⭐⭐⭐⭐ #2
Phase 4  AI Router 两层 + 80% 准确率 ⭐⭐⭐⭐⭐ #1
Phase 5  Eval 三维评分 + Diff + CI 三大门控 ⭐⭐⭐⭐⭐ #3
Phase 6  generation_metric 表 + 4 类 SQL 报表
Phase 7  ADR-0006/0007 收尾 + README 改写 → v1.0 锁定
Phase 8  配置统一化（v1.0→v1.1）+ 启动事故链复盘（10 项 #1–#10）
   │
Eval 增量（v1.1 后）
   ├─ 2026-06-21  MULTI_FILE + VUE 0 分修复（19.75 → 55.06）
   ├─ 2026-06-21  VUE Render 评分器修复（揭示 LLM 抽样波动）
   └─ 2026-06-22  3 轮均分 + temperature=0 + 断点续跑 ⭐⭐⭐⭐⭐ #4
```

详见 [`docs/roadmap/`](./docs/roadmap/) 完整路线图。

---

## 仓库导览

```
PROJECT_CHARTER.md          ← 项目宪法（目标 / 范围 / 9 项「不做」/ 5 条 AI 协作原则）
AGENTS.md                   ← AI 助手协作入口（agents.md 规范）

docs/
  governance/               ← 协作规则、ACP 模板、DoD
  adr/                      ← 13 篇架构决策记录
  roadmap/                  ← 里程碑 / 当前 phase / backlog
  tasks/                    ← 20 篇工作留痕（含治理违规复盘）
  architecture/             ← 设计文档（system / module / router / agent / eval / metric）
  reverse-engineering/      ← 教学版项目分析（Phase -1 资产）

eval/
  schema/case.schema.yaml   ← 评测 case 字段定义
  cases/*.yaml              ← 25 个评测用例
  reports/
    baseline-real.md                    ← 最新真实 LLM 多轮聚合 baseline
    baseline-real.55.06.bak.md          ← v1 单跑历史 (Render 修前)
    baseline-real.v1-single-run-35.38.bak.md  ← v1 单跑历史 (Render 修后, 暴露随机性)
    runs/baseline-real.round-{1,2,3}.{md,json}  ← 多轮中间报告

src/main/java/com/prompt2app/
  app/                      ← 业务领域（controller/service/mapper/model）
  router/                   ← AI Router（Phase 4 ⭐ #1）
  agent/                    ← Agent + Tool + Codegen（Phase 3 ⭐ #2）
  eval/                     ← 评测框架 + 多轮聚合（Phase 5 ⭐ #3, ADR-0013 ⭐ #4）
  metric/                   ← 生成质量埋点（Phase 6）
  infra/                    ← 横向基础设施

sql/generation_metric.sql   ← Phase 6 表结构
```

---

## 技术栈

- **后端**：Spring Boot 3 / **LangChain4j 1.5.1** / MyBatis-Flex / Redis / MySQL
- **前端**：Vue 3 / Vite / Ant Design Vue（**不重构**——Charter §3 红线）
- **AI 模型**：DeepSeek（主） + 路由层小模型 + LLM-as-Judge
- **构建**：JDK 21（Lombok 兼容硬要求）+ Maven
- **测试**：JUnit 5 + Mockito（32 测试类，eval 包 56 测试方法）
- **持久化**：Jackson JSON（评测中间报告，`@Jacksonized` Lombok 反序列化）

---

## 数据：项目体量速览

| 维度 | 数字 |
| --- | --- |
| 完成 Phase | 8 / 8 + Eval 增量 |
| ADR | **13** 篇 Accepted（含 2 反向 ADR）|
| Task Records | **20** 篇（含 2 次治理违规复盘）|
| 单元测试类 | 32 个 |
| eval 包测试方法 | 56 个 |
| 评测 case | 25 个 × 3 轮 |
| 最新评测总均分 | `40.83 ± 7.52` / 100（3 轮均分，HTML 80.18 / MULTI 57.44 / VUE 0.00） |
| Git 提交 | 详见 git log |

---

## 快速运行（本地）

```bash
# 1. 启动 MySQL + Redis（自备或 docker compose）
# 2. 创建数据库 + 导入表
mysql -u root -p < sql/create_table.sql
mysql -u root -p < sql/generation_metric.sql

# 3. 配置 .env（复制 .env.example 并填 DeepSeek API Key）
cp .env.example .env

# 4. 启动后端
JAVA_HOME=/path/to/jdk21 ./mvnw spring-boot:run

# 5. 跑评测包 56 单测（不依赖外部 LLM/DB）
./mvnw test -Dtest='com.prompt2app.eval.*Test'

# 6. 跑真实 LLM 评测（3 轮 × 25 case ≈ 3 小时，需 LLM API Key + DB/Redis 在线）
./mvnw test -Dtest=RealEvalRunner -Dspring.profiles.active=dev

# 7. 看评测报告
cat eval/reports/baseline-real.md
```

> **JDK 21 是硬要求**——Lombok 与 JDK 25+ 不兼容。
> **断点续跑**：评测中途死掉，直接重跑同一命令即可（`eval/reports/runs/round-N.json` 已存在的轮会跳过 LLM 调用）。

---

## 简历段落（直接抄）

> **Prompt2App · AI 网页生成平台**（个人作品集，[github.com/pppisnew/Prompt2App](https://github.com/pppisnew/Prompt2App)）
>
> 基于 Spring Boot 3 + LangChain4j 1.5.1 构建。从教学微服务 demo 重构为模块化单体 AI 工程项目，通过 **13 篇 ADR + 20 篇 Task Records** 完整记录决策与演进。
>
> 核心交付：
> - **AI Router 规则 + LLM 兜底两层路由**（25 case 评测 80% 准确率 / 88% 命中率）
> - **Tool Calling Agent 三层安全防御**（27 单测覆盖路径穿越 / 软链接逃逸 / 调用熔断）
> - **真实 LLM 三维评测体系**：编译 + 渲染 + LLM-as-Judge，25 case × 3 轮均分 ± 标准差（最新 baseline：**40.83 ± 7.52** / 100）
> - **评测稳定性治理**（ADR-0013）：识别 LLM 单跑波动 ±20 分问题，引入 `temperature=0` + 多轮均分 + 断点续跑机制，让"代码回归 vs LLM 抽样噪声"可量化区分
> - **生成质量指标埋点**：15 字段 metric 表 + 4 类聚合 SQL 报表
>
> **工程治理**：明确写下 9 项「不做清单」并用反向 ADR 论证（不做微服务 / 不做 Workflow / 不做 OSS），把"不堆砌"作为显式约束。诚实记录 2 次治理违规并事后复盘——比"完美履历"更说明真实工程现场。

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
- **> 30 分钟的工作必须留 Task Record**（Charter §4）
- **偏离 Task Record §4 的设计选择必须先取得作者许可**（2026-06-22 教训）

---

## License

参见 [`LICENSE`](./LICENSE)（如有）。
