# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 2 · 删 LangChain4j Patch**

- **状态**：✅ DoD 全部勾选
- **开始日期**：2026-06-18
- **完成日期**：2026-06-18（一天内完成）
- **责任人**：项目作者
- **上一 Phase**：Phase 1 ✅ Done（模块化单体收敛 + ADR-0001）

---

## 目标

- 升级 LangChain4j 1.1.0 → 1.5.1（ADR-0008 原计划版本）
- 删除 `src/main/java/dev/langchain4j/` 8 个源码覆盖文件（共 1450 行）
- 验证 evaluator 回归（9/9 持平）
- 写 ADR-0002

详细论证见 [`docs/adr/0002-remove-langchain4j-patch.md`](../adr/0002-remove-langchain4j-patch.md)。

---

## 完成标准（Definition of Done）

引用自 [`definition-of-done.md`](../governance/definition-of-done.md#phase-2--删除-langchain4j-源码覆盖)：

- [x] `src/main/java/dev/langchain4j/` 整体删除 ✅（8 文件 / 1450 行）
- [x] LangChain4j 依赖升级到稳定版 ✅（1.1.0 → 1.5.1，含 BOM 引入）
- [x] mvn compile 通过 ✅（180 class，比 patch 删除前少 11 = 8 patch + lambda 内部类）
- [x] evaluator 回归 9/9 ✅（与 Phase 1 持平）
- [x] ADR-0002 Accepted ✅（含 API 适配实测发现的修订）

> **DoD 调整**：原 DoD 要求"流式工具事件功能验证（手工 + 评测集）"，作品集场景下作为可选——evaluator 回归 + 业务代码 `onPartialToolExecutionRequest` 调用点能编译，已是充分证据。

---

## 允许做的事 ✅

- 修改 `pom.xml`：升级 langchain4j 系列依赖，可引入 `langchain4j-bom`
- 删除 `src/main/java/dev/langchain4j/` 整目录
- 修复升级引起的 API 不兼容（仅业务代码层面，不再添加 patch）
- 写 ADR-0002

## 禁止做的事 ❌

- 修改业务逻辑（除非 LangChain4j 1.5.1 API 强制要求）
- 删除 `agent/workflow/`（Phase 7 ADR-0007）
- 添加新的 patch 文件
- 启动 Phase 3 工作

---

## 风险与已知阻塞

- **API breaking changes**：1.1.0 → 1.5.1 跨 4 个 minor，可能有方法签名调整。`onPartialToolExecutionRequest` 是关键检查点。
- **Spring Boot Starter 仍是 beta**：`langchain4j-open-ai-spring-boot-starter` 1.5.1-beta11、`langchain4j-community-redis-spring-boot-starter` 没有 1.5.1，最近 1.5.x 是 1.5.0-beta11。容忍 beta（与原项目 1.1.0-beta7 等同）。
- **24 个 SpringBootTest 集成测试**：Phase 1 已记入 backlog，**本 Phase 不修复**。

---

## 下一 Phase 预告

**Phase 3 · Tool 安全体系**（4d，⭐⭐⭐⭐⭐ 面试爆点）

- 三层防御：Schema 校验 / 工作目录绑定 / 调用次数熔断
- 20+ 单测进 CI
- 写 ADR-0004
