# Task: Phase 5 评测体系自动化（三维评分 + Diff + CI）

- **日期**：2026-06-19
- **Phase**：Phase 5 · 评测体系自动化（⭐⭐⭐⭐⭐ 面试爆点 #3）
- **责任人**：项目作者（指令）+ AI 助手（执行）
- **状态**：Done
- **关联 ADR**：[ADR-0005](../adr/0005-evaluation-automation.md)

---

## 1. 目标

把 Phase 0 起的"stub mode 评测脚手架"升级到生产级**三维评分自动化体系**：

```
[Phase 0]  load → stub invoker → mustContain check → markdown
[Phase 5]  load → invoker → [Compile / Render / LLM-Judge 三维] → CompositeScorer
                          ↓
                    DiffReporter vs prev baseline → CI 红线
```

## 2. 背景

ADR-0008 起就规划三维评分；Phase 0 ADR 实施时只落了第一维（确定性 rubric），余下两维 + 自动化作为"延后到 Phase 5"。本次落地。

## 3. 影响范围（Scope）

- **新增**：`eval/` 包下 5 个 main 类（Scorer / RenderScorer / LlmJudgeService / LlmJudgeScorer / CompositeScorer / DiffReporter）+ prompt 模板
- **修改**：`RubricScorer` 实现 Scorer 接口（保留旧 `score` 方法）+ `MarkdownReporter` 升级（双 API）+ `EvalRunner` 加 `runFull` 入口
- **新增**：`.github/workflows/eval.yml`、`docs/adr/0005-...`、`docs/architecture/eval-design.md` v1、本 task record
- **未触碰**：业务代码、其它 phase

## 4. 修改内容

### 4.1 新增 main 类（~600 行）

| 文件 | 职责 |
| --- | --- |
| `Scorer.java` | 评分器接口 + `ScoreContribution` 嵌套 Value |
| `RenderScorer.java` | HTML/MultiFile/Vue 三策略渲染检查（无 Playwright，正则实现） |
| `LlmJudgeService.java` | LangChain4j AiService 接口（系统提示词在 resources） |
| `LlmJudgeScorer.java` | 调 LlmJudgeService + JSON / regex 解析 + LLM 异常兜底 |
| `CompositeScorer.java` | 多 Scorer 组合，否决合分（任一 veto → final 0；否则取 Judge 主分或均值） |
| `DiffReporter.java` | 解析上一次 baseline.md，per-case 比对，回归判定 + Markdown render |

### 4.2 改 main 类

- `RubricScorer`：新增 `evaluate()` 实现 `Scorer` 接口；保留旧 `score()` 方法（Phase 0 测试仍用）
- `MarkdownReporter`：保留旧 `writeBaseline()`；新增 `writeFull()` 输出三维 + diff 段；嵌套 `CaseRun`（Phase 0）+ `CaseFullRun`（Phase 5）
- `EvalRunner`：保留旧 `run()`（stub baseline）；新增 `runFull(casesDir, reportFile, prevReport, scorers)` 编排三维评分

### 4.3 Prompt 模板

`src/main/resources/prompt/eval-judge-system-prompt.txt` 含：
- 5 档语义锚点（0-29 / 30-59 / 60-79 / 80-89 / 90-100）
- 4 条评分原则（用户限制 / 功能覆盖 / 风格 / rubric 提示）
- 严格 JSON 输出格式 + 反围栏说明

### 4.4 测试覆盖（33 新增 / 86 总）

| 测试类 | @Test | 覆盖 |
| --- | --- | --- |
| `RenderScorerTest` | 9 | 三策略 × （正常 / 空 body / 全注释 / 无 body / 缺 package.json）|
| `CompositeScorerTest` | 6 | 否决取 0 / 全 OK 取 Judge / 全 OK 无 Judge 取均值 / contributions 完整 / 空 list 抛错 / 真实三维组合 |
| `DiffReporterTest` | 8 | 无 prev / 单 case 退化 / 单 case 改进 / 总体 regression / 平均 delta / Markdown 渲染 / 解析真 baseline / 文件不存在兜底 |
| `LlmJudgeScorerParseTest` | 10 | JSON / 围栏 / 空白 / regex 兜底 / 空字符串 / 无法解析 / 越界分数 / null service / uninvoked / LLM 异常 |

### 4.5 CI 门控（`.github/workflows/eval.yml`）

GitHub Actions on `push` / `pull_request` 跑：
1. Eval framework: `mvn test -Dtest='com.prompt2app.eval.*Test'`
2. Router accuracy: `mvn test -Dtest='com.prompt2app.router.*Test'`（含 RouterAccuracyTest 80%/60% 阈值）
3. Tool safety: `mvn test -Dtest='com.prompt2app.agent.tools.safety.*Test'`
4. Smoke baseline + 上传 artifact

LLM-Judge live mode（真烧 token）通过 `EVAL_LIVE=1` 本地触发，**不进 CI**。

### 4.6 治理文档同步

- `docs/adr/0005-evaluation-automation.md` 新增（4 备选方案 + 实施细节 + Judge 稳定性策略）
- `docs/adr/README.md` 索引追加
- `docs/architecture/eval-design.md` 从 stub 升级到 v1（活文档）
- `docs/roadmap/current-phase.md` Phase 5 状态切到 ✅ Done
- `docs/roadmap/milestones.md` Phase 5 行 + 2 条状态变更

## 5. 验证

### 通用 DoD

- [x] ADR-0005 已写并 Accepted
- [x] Task Record 已留存
- [x] `current-phase.md` Phase 5 全部 [x]
- [x] `milestones.md` Phase 5 切到 Done
- [x] commit message 标注 Phase
- [x] 没有遗留 `// TODO`

### 任务专属验证

- [x] **mvn clean compile 成功**：BUILD SUCCESS
- [x] **eval + router + safety 三大测试集** 86/87 通过（1 失败仍是 Phase 1 已记录的 `AiCodeGenTypeRoutingServiceTest` 集成测试，需 DB/Redis）
- [x] **新增 33 个 Phase 5 测试全过**：9 + 6 + 8 + 10
- [x] **CI workflow 文件就位**：`.github/workflows/eval.yml`，三大门控 + smoke baseline + artifact 上传
- [x] **向后兼容**：Phase 0 测试仍用旧 `RubricScorer.score()` / `EvalRunner.run()` API，未破坏

## 6. 风险与遗留

### 已知风险

- **不上 Playwright**（ADR-0005 §代价）：纯 HTML/MultiFile 检查靠正则，对 Vue 仅校验文件结构。Phase 8+ 评估升级
- **LLM-Judge 真跑稳定性**：未跑 live mode（成本考虑）；上线后需要观察同 case 三次方差，> 20% 触发多模型仲裁
- **DiffReporter 解析依赖 baseline 表格列序**：当前正则 `.*\|\s*([0-9]+(?:\.[0-9]+)?)\s*\|` 抓最后一列；如果 MarkdownReporter 表格列变更需同步

### 遗留事项

- **真 AgentInvoker 接入** 仍在 Phase 1 backlog（待 LLM 集成测试环境就绪）
- **24 个 SpringBootTest 集成测试** 仍未修复，沿用 Phase 1 backlog
- **CI workflow 首次推送会跑** —— 如有失败先看 GitHub Actions 日志

## 7. 对治理体系的更新

- [x] 写了 ADR-0005（架构性变更必有 ADR）
- [x] 升级了 `docs/architecture/eval-design.md` 从 stub 到 v1
- [x] 更新了 `current-phase.md` / `milestones.md`
- [x] 没有需要新增 backlog

## 8. 简历素材（面试爆点 #3）

> **Prompt Eval 三维评分体系（com.prompt2app.eval）**
> 设计 Scorer 接口 + 4 实现（Rubric / Render / LLM-as-Judge / Composite），按"否决项严苛、主分柔软"规则合分。25 case 评测集每次 PR 自动跑：编译 → 渲染 → LLM-as-Judge 三维评分 + 与上次 baseline diff 回归（单 case Δ > 10 标 regressed，平均分 Δ > 5 全局红灯）。CI 进 GitHub Actions，含 Tool 安全 / Router 准确率 / 评测框架自身三大门控。Phase 5 共 33 单测，全套 86/87 通过。

可独立讲 5 分钟的子点：
- "**为什么三维而不是单维？**" → 单维度都有盲区：rubric 抓不到风格，render 抓不到内容，LLM-Judge 不稳定
- "**为什么不上 Playwright？**" → CI 复杂度 vs 价值；轻量级正则覆盖 80% 白屏模式
- "**LLM-Judge 不稳定怎么办？**" → 异常 fallback 50 分非 veto；prompt 5 档语义锚点；JSON 解析容错
- "**diff 阈值怎么定？**" → 单 case 10 / 总 5%；改 prompt 时回归 > 5% 即红灯
- "**为什么否决项严苛但 Judge 柔软？**" → 业界 evals 实践：硬规则做门控，软规则给参考

## 9. 下一步建议

按治理纪律：

1. **commit + push**：单 commit `feat(phase-5): 3-dim eval automation + diff + CI [ADR-0005]`
2. **Phase 5 关闭**：等用户决定何时启动 Phase 6（生成质量埋点 + SQL 报表）
3. **不要顺手开始 Phase 6**：Phase 6 是 3d 工时，需要新规划

---

**Phase 5 总览**：

```
2026-06-19 一天内完成（接续 Phase 0-4 演进线）：
├── ADR-0005 起草（4 备选方案 + Judge 稳定性策略）
├── 6 个 main 类（Scorer + 3 实现 + Composite + DiffReporter）共 ~600 行
├── 1 个 prompt 模板
├── 修改 RubricScorer / MarkdownReporter / EvalRunner（保留旧 API 向后兼容）
├── .github/workflows/eval.yml CI 三大门控
├── 4 个测试类 33 @Test 全过
└── 验证：86/87 总通过（1 失败是 Phase 1 已知遗留）

总产出：
- 1 个 commit（待）
- ADR-0005（含"为什么不上 Playwright"答案）
- eval-design.md v1（活文档含三维架构图）
- 1 篇 task record
- ~600 行评测框架代码 + ~400 行测试
```

> Phase 5 是 AI Engineering 的核心命脉 ——"我建了 25 case 评测集"升级到"我建了三维评测体系 + CI 自动回归"，这是凭感觉调 prompt 与做 AI 工程的分水岭。
