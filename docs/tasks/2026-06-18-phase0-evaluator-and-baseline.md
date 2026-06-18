# Task: 评测执行器 v0 + baseline.md 跑通（Phase 0 收官）

- **日期**：2026-06-18
- **Phase**：Phase 0 · 评测基线（最后一道闸门）
- **责任人**：项目作者（指令）+ AI 助手（执行）
- **状态**：Done
- **关联 ADR**：[ADR-0008](../adr/0008-evaluation-first.md)

---

## 1. 目标

完成 Phase 0 DoD 中剩余两项：
- 评测执行器（`com.prompt2app.eval.*`）跑通端到端
- `eval/reports/baseline.md` 已生成

并附带验证 ADR-0009 task record 中挂着的"未跑 mvn compile"风险。

## 2. 背景

按 ADR-0008，评测体系是后续所有重构的"尺子"。Phase 0 已完成 schema + 25 case 准备，最后一步是把"机器"建起来——读 YAML、调 Agent、评分、出报表。

技术权衡（在上轮已与用户确认）：
- 直接调 Service ✓ vs HTTP SSE
- 读文件 ✓ vs 读 SSE 流
- JUnit 5 ✓ vs 独立 main
- Markdown 简洁版 ✓ vs JSON+Markdown
- Playwright 渲染检查 → Phase 5
- LLM-as-Judge → Phase 5
- **真 LLM 调用 → Phase 1+**（依赖 langchain4j patch 删除后稳定 compile）

## 3. 影响范围（Scope）

- **新增代码（仅 `com.prompt2app.eval`）**：6 main + 3 test = 9 文件
- **数据**：生成 `eval/reports/baseline.md`
- **文档**：`docs/roadmap/{current-phase, milestones, backlog}.md` 同步状态
- **未触碰**：业务代码、pom.xml（依赖未变）、ADR、其它 case 文件

## 4. 修改内容

### 4.1 evaluator 代码（main，6 文件）

| 文件 | 职责 |
| --- | --- |
| `EvalCase.java` | YAML 文件的 Java POJO（含嵌套 Rubric / Baseline） |
| `EvalCaseLoader.java` | SnakeYAML 加载 + snake_case → camelCase 自动映射 |
| `AgentInvoker.java` | Agent 调用接口 + `Stub` 默认实现（标记 `invoked=false`） |
| `RubricScorer.java` | 三段式确定性评分：mustContain 命中率 - mustNotContain 惩罚 - minFiles 惩罚 |
| `MarkdownReporter.java` | 写 baseline.md（Distribution + Summary + Per-Case Detail） |
| `EvalRunner.java` | 编排器（load → invoke → score → report），带 `main` 方法 |

### 4.2 测试（test，3 文件，9 个 @Test）

| 测试类 | 数量 | 覆盖 |
| --- | --- | --- |
| `EvalCaseLoaderTest` | 3 | 加载 25 case + 分布断言（HTML 7 / MultiFile 8 / Vue 10）+ 必填字段校验 + minFiles 字段约束 |
| `RubricScorerTest` | 5 | uninvoked → 0 分 / 全命中 → 100 分 / 反模式扣分 / minFiles 惩罚 / 大小写不敏感 |
| `EvalRunnerSmokeTest` | 1 | 端到端：25 case 全跑通 stub 模式 → 生成 baseline.md → 内容断言 |

### 4.3 治理文档同步

- `current-phase.md`：DoD 全部 [x]，状态从 `In Progress` → `✅ Done`
- `milestones.md`：Phase 0 状态从 🟡 → ✅ Done，新增状态变更条目
- `backlog.md`：标记"mvn compile 兜底"为已完成 ✅；新增"JDK 21 锁定"和"接入真 AgentInvoker"两条供 Phase 1 使用

## 5. 验证

### 通用 DoD

- [x] 该 Phase 的 ADR 已写并 Accepted（ADR-0008）
- [x] 至少 1 篇 Task Record 已留存（本文件 + 前两篇）
- [x] `current-phase.md` Phase 0 全部 [x]
- [x] `milestones.md` Phase 0 状态切换到 Done
- [x] commit message 标注 Phase（`feat(phase-0): ...`）
- [x] 没有遗留 `// TODO` 在主路径代码

### 任务专属验证（已实测）

- [x] **mvn compile 成功**：191 class 产出（含 8 个 langchain4j patch + 15 个 eval class）。这是 ADR-0009 task record 中挂着的"未跑 mvn compile"红旗，本次摘除。
- [x] **mvn test 9/9 通过**：
  ```
  EvalRunnerSmokeTest:    1/1
  EvalCaseLoaderTest:     3/3
  RubricScorerTest:       5/5
  Total:                  9/9
  Time:                   3.167 s
  ```
- [x] **baseline.md 已生成**：`eval/reports/baseline.md`，含 Distribution（HTML 7 / MultiFile 8 / Vue 10）、Summary（Invoked: 0/25 stub mode）、25 行 Per-Case Detail 表
- [x] **Loader snake_case 映射验证**：`expected_strategy` / `must_contain` / `min_files` 等字段全部成功绑定到 camelCase POJO 属性
- [x] **Scorer 数学正确性**：containRatio - notContainPenalty - minFilesPenalty 公式经 5 个单测覆盖

### Phase 0 整体收尾验证

```
✅ schema 定义
✅ 25 case 全部就位（HTML 7 / MultiFile 8 / Vue 10）
✅ 项目重命名（ADR-0009 越界批准 + 已完成）
✅ 评测执行器（com.prompt2app.eval.*）跑通端到端
✅ baseline.md 已生成（stub mode 结构性 baseline）
✅ ADR-0008 / ADR-0009 Accepted
```

**Phase 0 全部 DoD 满足，可以切换到 Phase 1。**

## 6. 风险与遗留

### 已知风险

- **stub 模式 baseline 价值有限**：当前 baseline.md 显示 Invoked 0/25，所有 case 的 must_contain 命中率 0/N。这只能证明 evaluator 流水线正确，不能反映"现版项目的真实生成质量"。
- **缓解**：把"接入真 AgentInvoker"作为 Phase 1 backlog 项，待 Phase 1 模块化重组 + Phase 2 删除 langchain4j patch 后，重跑 baseline.md 形成真正的对照基线。期间所有 Phase 评测都使用 stub baseline 作为"流水线健康"指标，**不**作为质量指标。

### 已知不完美

- **baseline.md 在 stub 模式下不区分 case 之间的差异**：每个 case 都是 0 分，分布表是唯一信息。Phase 1 接入真 invoker 后会显著拉开。
- **Lombok 与 JDK 25 不兼容**：本机 brew 默认 JDK 是 25，必须切到 JDK 21。已在 backlog 中记录，Phase 1 启动时会在 pom.xml 锁定 `<maven.compiler.release>21</maven.compiler.release>`。

### 遗留事项（已记入 backlog.md）

- **JDK 版本锁定**（Phase 1）
- **真 AgentInvoker 接入**（Phase 1+）
- **evaluator v1 增强**（Phase 5）：Playwright 渲染检查 + LLM-as-Judge + diff-against-prev-baseline + CI 集成

## 7. 对治理体系的更新

- [x] 更新了 `docs/roadmap/current-phase.md`：DoD 全部 [x]，状态切换到 ✅ Done
- [x] 更新了 `docs/roadmap/milestones.md`：Phase 0 行 + 状态变更记录
- [x] 更新了 `docs/roadmap/backlog.md`：完成项标记 ✅ + 新增 2 条 Phase 1 任务
- [x] 没有需要新增 ADR（evaluator v0 是 ADR-0008 既定方案的实现，无新决策）

## 8. 下一步建议

按治理纪律：

1. **commit + push** 本次 evaluator 代码与 baseline.md。
2. **Phase 0 关闭**：把 `current-phase.md` 切换到 Phase 1 占位，等用户决定何时开 Phase 1。
3. **不要顺手开始 Phase 1**：Phase 1 涉及删除 microservice 子目录、按领域分包、写 ADR-0001——这些都需要新的任务规划，应该等用户拍板再启动。

---

**Phase 0 总览**：

```
2026-06-18 一天内完成：
├── Phase 0 起手：建治理体系（Charter / AGENTS / governance / roadmap / tasks / architecture）
├── Phase 0 评测：schema + 3 示范 case + ADR-0008
├── Phase 0 越界：项目重命名 com.yupi.yuaicodemother → com.prompt2app（ADR-0009，178 文件）
├── Phase 0 case：补满到 25 case（HTML 7 / MultiFile 8 / Vue 10）
└── Phase 0 收官：evaluator 9 文件 + 9/9 测试通过 + baseline.md（stub 模式）

总产出：
- 4 个 commit
- ~2 万 token 文档（Charter / 8 篇 ADR / 4 篇 task record）
- 9 个 evaluator java 类
- 25 条评测 case
- 1 份 baseline.md
- 0 行业务代码改动（除项目重命名机械替换）
```

> Phase 0 的目标是"建尺子"——尺子建好了。
