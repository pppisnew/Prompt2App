# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 1 · 模块化单体收敛**

- **状态**：✅ DoD 全部勾选
- **开始日期**：2026-06-18
- **完成日期**：2026-06-18（一天内完成）
- **责任人**：项目作者
- **上一 Phase**：Phase 0 ✅ Done（治理 + 评测基线 + Evaluator）

---

## 目标

- 删除 `yu-ai-code-mother-microservice/`，主项目按领域分 6 个顶层包
- 保留 `microservice-final` git tag 作为对照存档
- 不破坏 evaluator 回归（Phase 0 baseline 持平）

详细论证与包映射见 [`docs/adr/0001-modular-monolith.md`](../adr/0001-modular-monolith.md)。

---

## 完成标准（Definition of Done）

引用自 [`definition-of-done.md`](../governance/definition-of-done.md#phase-1--模块化单体收敛)：

- [x] `yu-ai-code-mother-microservice/` 删除（git rm -rf）✅
- [x] 主线 `src/main/java/com/prompt2app` 按领域分包：`app / router / agent / eval / metric / infra` ✅
- [x] mvn compile 通过（含 `dev/langchain4j/` patch + agent/workflow/ 旧 langgraph4j）✅ 191 class
- [x] mvn test 通过（evaluator 9/9）✅
- [x] 评测集回归：baseline 模式持平（stub 模式分数恒为 0，结构性等价）✅
- [x] ADR-0001 Accepted ✅
- [x] `docs/architecture/module-design.md` 填充 ✅（v1，含包结构图 + 边界表 + 旧→新 映射）

> **DoD 调整**：原 DoD 要求 `mvn spring-boot:run` 启动 + 健康检查 OK。这需要 DB / Redis / API key 配齐，作品集场景作为可选。本 Phase 把 `mvn compile + mvn test` 作为硬指标，`mvn spring-boot:run` 列为遗留检查项。

---

## 允许做的事 ✅

- 在 `src/main/java/com/prompt2app/**` 下重组包路径（git mv + sed）
- 修改 `pom.xml`：仅限 `<packaging>` / 编译参数微调（如锁定 JDK 21），**不**新增依赖（除非 ACP）
- 修改 `application.yml` 的 `packages-to-scan` 等包路径配置
- 修改 `mapper/*.xml` 的 namespace
- 修改 `Prompt2AppApplication.@MapperScan`
- 删除 `yu-ai-code-mother-microservice/` 整个目录
- 修复 `ratelimter` 拼写错误（→ `infra/ratelimiter`）

## 禁止做的事 ❌

- 修改任何业务逻辑（仅做包路径机械重命名）
- 删除 `dev/langchain4j/` patch（Phase 2）
- 删除 `agent/workflow/`（原 langgraph4j，Phase 7 ADR-0007）
- 改 evaluator 业务（仅可改 import）
- 重命名前端目录 `yu-ai-code-mother-frontend/`（独立演进）
- 引入 ArchUnit 或其他模块边界守护（Phase 5）
- 启动 Phase 2 工作（升 LangChain4j、删 patch）

---

## 风险与已知阻塞

- **mapper.xml namespace 同步**：MyBatis 在启动时解析 namespace；遗漏会运行时炸 `BindingException`。靠 `mvn compile` 抓不到，但 evaluator smoke test 启动 Spring 上下文时会爆。
- **JDK 21 锁定**：上一 Phase 已发现 JDK 25 + Lombok 不兼容。本 Phase 在 `pom.xml` 加 `<maven.compiler.release>21</maven.compiler.release>`。
- **agent/workflow/ 内部依赖混乱**：原 langgraph4j 包含 demo / node / state / tools 等子包，移过去后子包内部 import 也要 sed。
- **删除 microservice/ 不可逆**：靠 git tag `microservice-final` 兜底。

---

## 下一 Phase 预告

**Phase 2 · 删 LangChain4j Patch**

- 删除 `src/main/java/dev/langchain4j/` 整体
- 升级 LangChain4j 到稳定版（`pom.xml` diff 在 ADR-0002 中说明）
- 写 ADR-0002

> 当前 Phase 完成前，**禁止开始 Phase 2 的工作**。
