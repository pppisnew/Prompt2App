# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 3 · Tool 安全体系**（⭐⭐⭐⭐⭐ 面试爆点 #1）

- **状态**：✅ DoD 全部勾选
- **开始日期**：2026-06-18
- **完成日期**：2026-06-18（一天内完成）
- **责任人**：项目作者
- **上一 Phase**：Phase 2 ✅ Done（删除 LangChain4j patch + 升级 1.5.1）

---

## 目标

为 Tool Calling Agent 加三层安全防御，覆盖**真实存在的安全漏洞**：

```
[漏洞实测] FileWriteTool.writeFile("../../etc/passwd", "...")
[当前]     不报错，文件被写到 tmp/code_output 之外
[修复后]   ToolSafetyException + 测试证明
```

详细决策见 [`docs/adr/0004-tool-safety.md`](../adr/0004-tool-safety.md)。

---

## 完成标准（Definition of Done）

引用自 [`definition-of-done.md`](../governance/definition-of-done.md#phase-3--tool-安全体系)：

- [x] 三层防御实施：Schema 校验 / 工作目录绑定 / 调用次数熔断 ✅
- [x] **27 条单元测试**全部通过 ✅（超 20+ DoD 要求）
  - [x] 路径越权（`..` / 绝对路径 / `~` / null byte / 控制字符）
  - [x] 关键文件保护（package.json / vite.config.* / index.html 等 8 项）
  - [x] 调用次数熔断（session 总数 / 单文件 / 多 appId 隔离 / reset / 并发安全）
  - [x] 软链接逃逸（POSIX-only conditional test）
- [x] CI 中包含上述测试，失败即阻断合入 ✅（`mvn test -Dtest='...safety.*Test'`）
- [x] ADR-0004 Accepted ✅

---

## 允许做的事 ✅

- 在 `agent/tools/safety/` 下新建安全类（PathValidator / Sandbox / ToolCallCounter）
- 修改现有 6 个文件 tool（FileWriteTool / FileReadTool / FileEditTool / FileDeleteTool / FileDirReadTool / FileModifyTool）接入安全层
- 在 `src/test/java/com/prompt2app/agent/tools/safety/` 下新增测试
- 在 `infra/exception/` 新增 `ToolSafetyException`
- 修改 BaseTool（如需要传递 appId / sandbox 上下文）

## 禁止做的事 ❌

- 修改 evaluator 业务（Phase 5 任务）
- 升级 Pom 依赖（除非必需 + 走 ACP）
- 改 LangChain4j Tool 注解机制
- 启动 Phase 4 Router 重写

---

## 风险与已知阻塞

- **JUnit 5 软链接测试在 macOS/Linux 行为不同**：用 JUnit Pioneer 或 conditional 标注。如果不便测试，软链接 case 可在 ADR 中标记"已设计但 CI 不强制"。
- **FileSystem permissions in CI**：测试需要创建临时目录，CI 容器可能受限。用 `@TempDir` JUnit 5 内置即可。
- **测试时长**：20+ 测试目标控制在 < 5s 总执行时间。

---

## 下一 Phase 预告

**Phase 4 · AI Router 重写**（4d，⭐⭐⭐⭐⭐ 面试爆点 #2）

- 规则路由层（关键词 + 字数 + 历史成功率）
- LLM 兜底分类器（小模型 + few-shot）
- 路由决策埋点
- 写 ADR-0003
