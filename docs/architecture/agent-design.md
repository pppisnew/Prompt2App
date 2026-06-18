# Agent Design

> **状态**：v1（Phase 3 落地）
> **对应 Phase**：Phase 3 · Tool 安全体系
> **相关 ADR**：[ADR-0004](../adr/0004-tool-safety.md)

---

## Tool Calling Agent 主流程

```
用户需求
   ↓
[System Prompt] 项目背景 + 工具说明 + 安全约束
   ↓
[LLM] 选择工具 + 生成参数
   ↓
[Safety Layer 1] PathValidator 字符串校验  ────┐
[Safety Layer 2] Sandbox 工作目录绑定         ├── ToolSafetyException 时直接抛
[Safety Layer 3] ToolCallCounter 调用熔断  ────┘    LangChain4j 把错误传给 LLM 让其改策略
   ↓
[Tool Execution] 执行工具
   ↓
[Observation] 工具结果回传 LLM
   ↓
继续循环 OR 结束
```

## 三层安全（ADR-0004 落地）

| 层 | 类 | 时机 | 拒绝原因 |
| --- | --- | --- | --- |
| Layer 1 · Schema 校验 | [`PathValidator`](../../src/main/java/com/prompt2app/agent/tools/safety/PathValidator.java) | 入口（参数解析后） | `PATH_INVALID` / `PATH_ABSOLUTE` / `PATH_TRAVERSAL` |
| Layer 2 · 能力沙箱 | [`Sandbox`](../../src/main/java/com/prompt2app/agent/tools/safety/Sandbox.java) | 工具实际执行前 | `SYMLINK_ESCAPE` / `GUARDED_FILE` |
| Layer 3 · 行为熔断 | [`ToolCallCounter`](../../src/main/java/com/prompt2app/agent/tools/safety/ToolCallCounter.java) | 跨调用维度（按 appId） | `CIRCUIT_BREAKER` / `HIGH_FREQ_MOD` |

所有拒绝抛 [`ToolSafetyException`](../../src/main/java/com/prompt2app/infra/exception/ToolSafetyException.java)（带 `Reason` 枚举），LangChain4j 框架捕获后将错误消息回传给 LLM。

## 工具集

| 工具 | 操作类型 | Sandbox 方法 | 关键文件保护 | Counter 类型 |
| --- | --- | --- | --- | --- |
| `FileWriteTool` | 写入 | `resolveForWrite` | — | `WRITE` |
| `FileReadTool` | 读取 | `resolveForRead` | — | `READ` |
| `FileModifyTool` | 编辑 | `resolveForRead` | — | `EDIT` |
| `FileDeleteTool` | 删除 | `resolveForDelete` | ✅ 8 项相对路径黑名单 | `DELETE` |
| `FileDirReadTool` | 列目录 | `resolveForRead` | — | `LIST` |
| `ExitTool` | 退出 | 无路径参数 | — | （不计数） |

## 关键文件黑名单（`Sandbox.DEFAULT_GUARDED_PATHS`）

```
package.json / package-lock.json / pnpm-lock.yaml / yarn.lock
vite.config.js / vite.config.ts
tsconfig.json
index.html
```

> 这些文件**允许写入**（AI 需要能初始化或更新依赖）但**禁止删除**。

## 阈值（可配置）

```yaml
prompt2app:
  tool:
    max-per-session: 50    # 单会话工具调用上限
    max-per-file: 10       # 单文件被修改次数上限
```

通过 Spring `@Value` 注入。可在 `application.yml` 覆盖。

## 测试覆盖（27 单测，全过，1.5s 内）

```
PathValidatorTest    8 tests   纯字符串校验
SandboxTest         12 tests   含 POSIX 软链接 conditional
ToolCallCounterTest  7 tests   含并发安全
                    ──
                    27 tests
```

CI 命令：
```bash
mvn test -Dtest='com.prompt2app.agent.tools.safety.*Test'
```

任一失败即视为安全回归，阻断合入。

## 演进锚点

- 出现新攻击模式（例如 ReDoS 通过 prompt 注入） → 加 Layer 4（输入正则限速器）
- 阈值默认值不合适（合法长项目超 50） → 调高 + 引入"白名单工具不计数"
- 软链测试 CI flaky → 改用 jimfs 内存文件系统
- 引入 ArchUnit 后，强制 `agent/tools/` 必须经过 `agent/tools/safety/` 才能动 FS

## 参考

- [ADR-0004](../adr/0004-tool-safety.md) 完整决策
- 业界参考：[Anthropic - Tool Use Safety](https://docs.anthropic.com/en/docs/agents-and-tools/tool-use)、[OpenAI Function Calling Best Practices](https://platform.openai.com/docs/guides/function-calling)
