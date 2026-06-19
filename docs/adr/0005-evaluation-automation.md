# ADR-0005：评测体系自动化（三维评分 + diff + CI）

- **状态**：Accepted
- **日期**：2026-06-19
- **决策者**：项目作者
- **相关 Phase**：Phase 5 · 评测体系自动化
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

Phase 0 起手时已建立评测基础设施（ADR-0008 落地）：

```
eval/
├── schema/case.schema.yaml
├── cases/                    25 case (HTML 7 / MultiFile 8 / Vue 10)
└── reports/baseline.md       (stub mode 结构性 baseline)

src/main/java/com/prompt2app/eval/
├── EvalCase / EvalCaseLoader
├── AgentInvoker (interface + Stub)
├── RubricScorer              (must_contain / must_not_contain / minFiles)
├── MarkdownReporter
└── EvalRunner
```

**事实判断**：v0 评测器只做"确定性"那一维。生产级评测体系还需：
- **页面渲染检查**：编译过的代码也可能运行时白屏
- **主观质量评分**：mustContain 不能反映"信息密度 / 视觉层次 / 组件拆分"等主观维度
- **回归检测**：跑出分数了，但分数比上次降了多少？低 5% 是噪声还是退化？
- **CI 守护**：不进 CI 就是"装饰品评测"

ADR-0008 §决策已规划 v1 三维：编译 / 渲染 / LLM-Judge。**本 ADR 落地这一规划**，并补全 Phase 0 末尾"延后到 Phase 5"的所有项。

---

## 备选方案（Options）

### 方案 A：保持 v0 stub 模式不动

- 优点：零工作量
- 缺点：评测体系永远停在"装饰品"阶段；ADR-0008 的承诺空头
- 长期成本：高（之后每个 Phase 改 prompt 都没"客观裁判员"）

### 方案 B：三维评分 + 回归 diff + CI 守护（**本决策**）

三维评分组合：

| 维度 | 工具 | 性质 | 阈值 |
| --- | --- | --- | --- |
| **编译/构建** | 现有 RubricScorer 的 mustContain ratio + minFiles 校验 | 否决项 | hits < total → 0 分 |
| **渲染非空** | 轻量级正则/字符串检查（`<body>` 标签内非空文本 ≥ N 字） | 否决项 | textLength < 100 → 0 分 |
| **LLM-as-Judge 主观分** | LangChain4j AiService + Judge 系统提示词 | 主分 0-100 | < 60 红灯 |

最终合分：
```
finalScore = (compileOk && renderOk) ? llmJudgeScore : 0
```

任何一个否决项失败 → 总分 0；否则取 LLM-Judge 主分。

回归 diff：
- 对比上一次 `eval/reports/baseline.md`
- 单 case 分数下降 > 10 → 红灯
- 总平均分下降 > 5% → 红灯

CI：
- GitHub Actions 在 PR 时跑 evaluator stub 模式（无 LLM 成本）
- 路由准确率回归测试（已在 Phase 4 实现的 RouterAccuracyTest）
- Tool 安全测试（Phase 3 实现）
- "live mode"（真 LLM 调用）由开发者本地手动跑

### 方案 C：上 Playwright 做真渲染检查

- 优点：能抓 Vue 项目运行时错误（白屏、JS error）
- 缺点：
  - Playwright Java 需要下载浏览器二进制（~150MB）+ 系统依赖
  - CI 容器启动时间增加 30s+
  - 作品集场景下，Vue 工程的 `npm run build` 成功 + LLM-Judge "可点击" 已能覆盖大部分质量信号
- 长期成本：中。延后到 Phase 8+ 评估
- **本 Phase 不做**

### 方案 D：自己手写 prompt evaluation harness（不用 LLM-as-Judge）

- 优点：完全确定性
- 缺点：手写 rubric 永远抓不到"AI 写的代码风格如何"。AI Engineering 业界共识是 **LLM-as-Judge 不可替代**
- 长期成本：高（每加一个新评分维度都要手写规则）

---

## 决策（Decision）

**选择方案 B**：三维评分（确定性 + 渲染 + LLM-Judge）+ 回归 diff + CI stub 模式守护。

### 实施细节

#### 1. Scorer 接口与组合

```java
public interface Scorer {
    String name();                                          // "rubric" / "render" / "llm-judge"
    ScoreContribution score(EvalCase c, AgentInvoker.InvocationResult inv);
}

@Value @Builder
public class ScoreContribution {
    String dimension;        // 维度名
    double score;            // 0-100
    boolean veto;            // 是否触发否决
    String detail;           // 文本说明
}
```

CompositeScorer 组合多个 Scorer，按否决规则求最终分。

#### 2. RubricScorer（v1 已有，重命名 + 实现 Scorer）

保留现有 must_contain / must_not_contain / minFiles 逻辑，新接口适配。

#### 3. RenderScorer（新增，无 Playwright）

```java
// HTML 检查：找到 <body>...</body>，非 commented 文本 length ≥ 100
// MULTI_FILE 检查：每个 .html 文件单独跑 HTML 检查
// VUE_PROJECT 检查：仅校验 src/App.vue 非空 + main.{js,ts} 存在
```

不用 Playwright，避免 CI 复杂度。覆盖 80% 的"白屏"模式（生成器输出空 body 或全注释）。

#### 4. LlmJudgeScorer（新增）

```java
public interface LlmJudgeService {  // LangChain4j AiService
    @SystemMessage(fromResource = "prompt/eval-judge-system-prompt.txt")
    JudgeResult judge(String userPrompt, String generatedOutput, String rubricHints);
}

record JudgeResult(int score, String reasoning) {}
```

使用 routingChatModel（小模型，已存在）作为 judge model，避免引入新依赖。

#### 5. CompositeScorer

```java
public final class CompositeScorer {
    private final List<Scorer> scorers;
    public CaseFinalScore score(...) {
        // 收集每维度的 ScoreContribution
        // 否决项 fail → finalScore = 0
        // 全部 OK → finalScore = LLM-Judge 主分
    }
}
```

#### 6. DiffReporter

读上一次 `eval/reports/baseline.md`（或指定文件），按 case id 比对：

```
| ID | Prev | Cur | Δ | Status |
| 003-todo-app-vue | 75.0 | 82.5 | +7.5 | improved |
| 014-ngo-charity-site | 60.0 | 50.0 | -10.0 | regressed ⚠️ |
```

- Δ < -10 → regressed
- 平均 Δ < -5% → 总体红灯

#### 7. CI workflow `.github/workflows/eval.yml`

跑下列**不依赖外部 LLM 的**项：
- `mvn test -Dtest='com.prompt2app.eval.*Test'`（评测框架自身）
- `mvn test -Dtest='com.prompt2app.router.*Test'`（含 RouterAccuracyTest 路由准确率门控）
- `mvn test -Dtest='com.prompt2app.agent.tools.safety.*Test'`（Tool 安全门控）

任一失败 PR 红灯。

LLM-Judge 真跑（"live"）通过环境变量 `EVAL_LIVE=1` 开关，本地或夜间 cron 跑。

#### 8. 评测报告升级

`eval/reports/baseline.md` 表格新增列：`Compile / Render / Judge / Final / Δ`。

---

## 代价（Consequences）

### 正面

- **三维评分**：客观（确定性 + 渲染） + 主观（LLM-Judge），覆盖完整
- **CI 红线**：路由准确率 + Tool 安全 + 评测框架自身全进 CI
- **Diff 回归**：每次改 prompt / 升级 LangChain4j 都能定位"哪个 case 退化"
- **简历**：从"我建了 25 case 评测集"升级到"我建了三维评测体系 + CI 回归 + diff 报表"

### 负面

- **不上 Playwright**：渲染检查靠正则，对纯 HTML/MultiFile 够用，对 Vue 工程仅校验文件结构（不跑 npm build）。**接受**：作品集场景，Phase 8 再评估
- **LLM-Judge 成本**：本地跑 25 case × 3 = 75 次小模型调用 ~$0.5，CI 不跑（默认 stub）
- **Diff 首次空 baseline**：fallback 为"全 OK"，从第二次跑开始才有真实 diff

### 中性

- 现有 RubricScorer 改名 + 实现新接口，不改业务逻辑
- 现有 EvalRunner 升级为编排多 Scorer，向下兼容（旧测试不破）

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估：

- LLM-Judge 评分稳定性 < 80%（同 case 三次方差 > 20%）→ 改用多模型仲裁或 fewer-shot prompt
- 评测框架自身耗时 > 10 min/全跑 → 拆分 fast/slow tier
- CI 中 RouterAccuracyTest 频繁红灯 → 阈值过严，调到 70/55
- 用户反馈"评测说 OK 但实际不行" → 需要补 Playwright 或更细的渲染规则

---

## 关于 LLM-Judge 提示词稳定性

> Phase 0 ADR-0008 列为 v0 → v1 的关键不确定项。

实施时手段：
1. **Judge prompt 设计原则**：明确给出 0-100 分的语义锚点（"60: 基本满足需求"、"80: 视觉/交互优秀"），避免模型自行解读
2. **结构化输出**：要求模型返回 JSON `{score, reasoning}`，便于解析
3. **稳定性验证**：每次跑评测前用 3 个固定 case 跑 3 次，方差 > 20% 警报
4. **多模型仲裁**（未来）：如发现单模型不稳定，改用 2-3 模型平均

---

## 参考资料（References）

- [`docs/adr/0008-evaluation-first.md`](./0008-evaluation-first.md) §决策：Phase 0 起就规划本 Phase
- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §2 「核心能力 P0：Prompt Eval」
- [`docs/architecture/eval-design.md`](../architecture/eval-design.md) ← 本 ADR 落地后从 stub 升到 v1
- 业界参考：[Anthropic Evals Cookbook](https://github.com/anthropics/anthropic-cookbook/tree/main/misc)、[OpenAI Evals](https://github.com/openai/evals)
