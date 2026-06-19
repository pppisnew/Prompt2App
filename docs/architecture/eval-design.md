# Eval Design

> **状态**：v1（Phase 5 落地）
> **对应 Phase**：Phase 5 · 评测体系自动化
> **相关 ADR**：[ADR-0005](../adr/0005-evaluation-automation.md) + [ADR-0008](../adr/0008-evaluation-first.md)

---

## 三维评分架构

```
EvalRunner.runFull(casesDir, reportFile, prevReport, scorers)
  │
  ├─ EvalCaseLoader.loadAll  →  25 EvalCase
  │
  ├─ for each case:
  │     ├─ AgentInvoker.invoke()        →  InvocationResult
  │     └─ CompositeScorer.score()      →  CaseFinalScore
  │             ├─ RubricScorer    (must_contain / must_not_contain / minFiles)
  │             ├─ RenderScorer    (HTML <body> 非空 / Vue 工程结构)
  │             └─ LlmJudgeScorer  (LangChain4j AiService + JSON 解析)
  │
  ├─ DiffReporter.diff(currentScores, prevReport)  →  DiffReport
  │
  └─ MarkdownReporter.writeFull(...)  →  eval/reports/<sha>.md
```

## 类设计

```
eval/
├── EvalCase.java              POJO（Phase 0）
├── EvalCaseLoader.java        SnakeYAML loader（Phase 0）
├── AgentInvoker.java          interface + Stub（Phase 0）
│
├── Scorer.java                ★ Phase 5：评分器接口
├── RubricScorer.java          ★ Phase 5：实现 Scorer，保留旧 CaseScore API
├── RenderScorer.java          ★ Phase 5：HTML 非空检查（无 Playwright）
├── LlmJudgeService.java       ★ Phase 5：LangChain4j AiService 接口
├── LlmJudgeScorer.java        ★ Phase 5：调 LLM-Judge + JSON 解析 + 兜底
├── CompositeScorer.java       ★ Phase 5：多维度组合 + 否决合分
│
├── DiffReporter.java          ★ Phase 5：vs prev baseline 回归检测
├── MarkdownReporter.java      Phase 0 + Phase 5：双 API（baseline / full）
└── EvalRunner.java            Phase 0 + Phase 5：双入口（run / runFull）
```

## 合分规则

| 场景 | finalScore |
| --- | --- |
| 任一 Scorer veto = true | **0**（VETO） |
| 全部通过 + 含 llm-judge 维度 | LLM-Judge 主分 |
| 全部通过 + 无 llm-judge 维度 | 非 judge 维度算术平均 |

**设计原则**："否决项严苛，主分柔软"。
- Rubric / Render 是**硬门控**（缺关键内容 / 渲染失败 → veto）
- LLM-Judge 是**软评分**（中等分仍允许通过，但写入报表供人工 review）

## RenderScorer 三策略检查

| Strategy | 检查 |
| --- | --- |
| `HTML` | `<body>` 标签存在 + 内部去注释/标签后纯文本 ≥ 100 字符 |
| `MULTI_FILE` | merged output 中至少 2 个 `<body>` 标签（多文件已落地） |
| `VUE_PROJECT` | merged output 含 `package.json` + （`App.vue` 或 `main.{js,ts}`）|

> **不上 Playwright**（ADR-0005 §代价）：JSoup-free 实现覆盖 80% 白屏模式，CI 复杂度低。

## LlmJudgeScorer 设计

- **Judge 模型**：复用 `routingChatModelPrototype`（小模型，已存在），不增依赖
- **Prompt 模板**：`prompt/eval-judge-system-prompt.txt`，含 5 档语义锚点（0-29 / 30-59 / 60-79 / 80-89 / 90-100）
- **输出格式**：严格 JSON `{"score": 75, "reasoning": "..."}`
- **解析容错**：JSON 解析失败 → 正则抓 `"score": N` → 失败 → fallback 50 分
- **异常容错**：LLM 超时 / 抽风 → fallback 50 分 + 详细错误，**不 veto**（避免 LLM 抽风导致 CI 错误红灯）

## CI 门控（`.github/workflows/eval.yml`）

每次 push / PR 跑：

| 测试集 | 数量 | 阈值 | 失败后果 |
| --- | --- | --- | --- |
| `com.prompt2app.eval.*Test` | 36 | 全过 | PR 红灯 |
| `com.prompt2app.router.*Test` | 17 | 含 RouterAccuracyTest（hit≥80% / acc≥60%） | PR 红灯 |
| `com.prompt2app.agent.tools.safety.*Test` | 27 | 全过 | PR 红灯 |
| EvalRunnerSmokeTest | 1 | baseline.md 生成 + 上传 artifact | — |

LLM-Judge **真跑（live mode）** 不进 CI——通过本地 `EVAL_LIVE=1` 触发，避免每个 PR 烧 token。

## DiffReporter 回归判定

| 维度 | 阈值 | 行为 |
| --- | --- | --- |
| 单 case 分数下降 | > 10 绝对值 | 标记 `regressed` 🚨 |
| 单 case 分数上升 | > 10 绝对值 | 标记 `improved` ✅ |
| 总平均分下降 | > 5 绝对值 | 全局 `OVERALL REGRESSION` 🚨 |
| 无 prev baseline | — | 全部 `new` 🆕 |

## 测试覆盖（Phase 5 新增 33 + 历史 53 = 86）

| 类 | @Test |
| --- | --- |
| `RenderScorerTest` | 9 |
| `CompositeScorerTest` | 6 |
| `DiffReporterTest` | 8 |
| `LlmJudgeScorerParseTest` | 10 |
| **Phase 5 小计** | **33** |
| 历史（Phase 0/3/4） | 53 |
| **总计** | **86** |

实测：86/87 通过（1 失败是 Phase 1 已记入 backlog 的旧集成测试）。

## 演进锚点

- LLM-Judge 评分稳定性 < 80%（同 case 三次方差 > 20%） → 多模型仲裁
- 评测全跑耗时 > 10 min → 拆 fast/slow tier
- RouterAccuracyTest 在 CI 频繁红灯 → 阈值松到 70/55 或扩 case
- 用户反馈"评测说 OK 但实际不行" → 升级到 Playwright

## 参考

- [ADR-0005](../adr/0005-evaluation-automation.md) 决策论证
- [ADR-0008](../adr/0008-evaluation-first.md) Phase 0 起点
- [PROJECT_CHARTER.md](../../PROJECT_CHARTER.md) §2 「核心能力 P0：Prompt Eval 体系」
- 业界参考：[OpenAI Evals](https://github.com/openai/evals)、[Anthropic Cookbook](https://github.com/anthropics/anthropic-cookbook)
