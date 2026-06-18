# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 0 · 评测基线**

- **状态**：In Progress
- **开始日期**：2026-06-18
- **预计完成**：2026-06-19（2 工作日）
- **责任人**：项目作者

---

## 目标

在动任何业务代码之前，**先建立质量评估的"尺子"**。所有后续重构都要在这把尺子上对比基线。

详细动机见 [`docs/adr/0008-evaluation-first.md`](../adr/0008-evaluation-first.md)。

---

## 完成标准（Definition of Done）

引用自 [`definition-of-done.md`](../governance/definition-of-done.md#phase-0--评测基线)：

- [x] `eval/schema/case.schema.yaml` 完成
- [x] `eval/cases/` 共 25 条 case（HTML 7 / MultiFile 8 / Vue 10）✅
  - [x] 001, 004-009：HTML 7 条
  - [x] 002, 010-016：MultiFile 8 条
  - [x] 003, 017-025：Vue 10 条
- [x] 每条 case 的 `must_contain` / `must_not_contain` / `llm_judge_dimensions` 完整
- [ ] 评测执行器（`com.prompt2app.eval.*`）跑通端到端
- [ ] `eval/reports/baseline.md` 已生成
- [x] ADR-0008 Accepted

### Phase 0 临时任务（ADR-0009 越界批准）

- [x] 项目重命名为 Prompt2App（包路径 / pom.xml / 主启动类 / README）
- [x] ADR-0009 Accepted

---

## 允许做的事 ✅

- 在 `eval/` 下新增 / 修改 case
- 在 `eval/` 下编写评测执行器（Java 代码可以新增到 `src/main/java/com/prompt2app/eval/`）
- 在 `docs/adr/` 写 ADR-0008 附录或补充
- 在 `docs/tasks/` 留 Task Record
- 修文档勘误

---

## 禁止做的事 ❌

- 修改 `src/main/java/com/prompt2app/` 下除 `eval/` 之外的任何业务代码
- 修改 `yu-ai-code-mother-microservice/` 下任何代码（它在 Phase 1 删除）
- 修改 `dev/langchain4j/` 下源码覆盖文件（它在 Phase 2 删除）
- 修改 `pom.xml` 引入新依赖（除非评测执行器需要 + 走 ACP）
- 修改前端代码 `yu-ai-code-mother-frontend/`
- 升级 LangChain4j 版本（在 Phase 2 做）
- 修改数据库表结构
- 优化 Router / Agent / Prompt（在 Phase 4-5 做）

---

## 风险与已知阻塞

- **基线跑通需要调用 LLM**：会产生少量 token 成本（按 DeepSeek 估算 < $0.5）。需要确认 `application-local.yml` 中的 DeepSeek API Key 可用。
- **评测执行器涉及 Playwright 集成**：可能需要新增 `com.microsoft.playwright` 依赖——这是 Phase 0 范围内**唯一**允许新增的依赖（已在本 Phase 内隐含批准）。
- **LLM-Judge 提示词的稳定性**：v0 接受波动 ≤ 10%；超过后再 ADR-0005 中讨论。

---

## 下一 Phase 预告

**Phase 1 · 模块化单体收敛**

- 删除 `yu-ai-code-mother-microservice/`
- 主线包按领域分模块（app / router / agent / eval / metric / infra）
- 写 ADR-0001

> 当前 Phase 完成前，**禁止开始 Phase 1 的工作**。看到相关想法记到 [`backlog.md`](./backlog.md)。
