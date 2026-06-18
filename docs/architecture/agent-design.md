# Agent Design

> **状态**：Stub（待 Phase 3 完成后撰写）
> **对应 Phase**：Phase 3 · Tool 安全体系
> **相关 ADR**：ADR-0004（待写）

---

## 设计要点（Phase 3 启动时展开）

### Tool Calling Agent 主流程

```
用户需求
   ↓
[System Prompt] 项目背景 + 工具说明 + 安全约束
   ↓
[LLM] 选择工具 + 生成参数
   ↓
[Safety Layer] 三层防御（见下）
   ↓
[Tool Execution] 执行工具
   ↓
[Observation] 工具结果回传 LLM
   ↓
继续循环 OR 结束
```

### 三层安全

| 层 | 时机 | 实现 |
| --- | --- | --- |
| **Schema 校验** | 工具调用前 | JSON Schema 强校验，路径必须 relative |
| **能力沙箱** | 工具执行时 | 工作目录硬绑定 `tmp/code_output/{appId}/`；resolve 后必须 startsWith |
| **行为熔断** | 跨调用 | 单会话工具调用次数 ≤ N；同文件 10 次内修改告警 |

### 待详写

- [ ] 工具集清单（read / write / edit / delete / list）
- [ ] 每个工具的 JSON Schema
- [ ] 关键文件白/黑名单（package.json 可写、node_modules 禁删）
- [ ] 攻击用例 20+ 条（path traversal / symlink / 关键文件删 / 死循环）
- [ ] 安全测试组织（`src/test/java/.../tool/safety/`）

---

> _此处暂为占位。Phase 3 启动时按 [`README.md`](./README.md) §写作规范 撰写。_
