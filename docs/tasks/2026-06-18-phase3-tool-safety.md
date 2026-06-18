# Task: Phase 3 Tool 安全三层防御 + 27 单测

- **日期**：2026-06-18
- **Phase**：Phase 3 · Tool 安全体系（⭐⭐⭐⭐⭐ 面试爆点 #1）
- **责任人**：项目作者（指令）+ AI 助手（执行）
- **状态**：Done
- **关联 ADR**：[ADR-0004](../adr/0004-tool-safety.md)

---

## 1. 目标

按 ADR-0004 落地三层防御：

1. Layer 1 · `PathValidator`：纯字符串路径校验
2. Layer 2 · `Sandbox`：工作目录绑定 + canonical 路径越权 + 关键文件黑名单
3. Layer 3 · `ToolCallCounter`：调用次数熔断

并接入 5 个 file tool（FileWriteTool / FileReadTool / FileModifyTool / FileDeleteTool / FileDirReadTool），写 ≥20 单测进 CI。

## 2. 背景

Phase 3 起手时**实测**发现 `FileWriteTool.writeFile("../../etc/passwd", ...)` 当前完全不报错——`Paths.get(...).isAbsolute()` 返回 false 让校验逻辑跳过，但 `projectRoot.resolve(...)` 解析后真的写到 `/etc/passwd`。这是治理体系第一次"实测漏洞驱动设计"。

5 个安全漏洞的真实演示（写入 ADR-0004 §背景）：路径越权 / 绝对路径 / 关键文件按文件名匹配可绕过 / 无调用次数上限 / 无软链接检测。

## 3. 影响范围（Scope）

- **新增**：`agent/tools/safety/` 包（4 类）+ `infra/exception/ToolSafetyException`
- **修改**：5 个 file tool（接入 SandboxFactory + ToolCallCounter）
- **新增**：3 个测试类（PathValidatorTest / SandboxTest / ToolCallCounterTest），27 @Test 方法
- **新增**：`docs/adr/0004-tool-safety.md`、本 task record
- **更新**：`docs/architecture/agent-design.md` 从 stub 升到 v1
- **未触碰**：业务逻辑（仅替换路径解析）、其它 phase 的代码、evaluator

## 4. 修改内容

### 4.1 新增 5 个 main 类

| 文件 | 类型 | 行数 | 职责 |
| --- | --- | --- | --- |
| `agent/tools/safety/PathValidator.java` | 工具类（final + 私有构造） | ~75 | Layer 1 字符串校验，纯函数 |
| `agent/tools/safety/Sandbox.java` | 不可变值对象 | ~135 | Layer 2 工作目录绑定 + canonical 校验 + 关键文件黑名单 |
| `agent/tools/safety/SandboxFactory.java` | `@Component` | ~30 | 按 appId 创建 Sandbox |
| `agent/tools/safety/ToolCallCounter.java` | `@Component` | ~115 | Layer 3 跨调用熔断，含 ConcurrentHashMap + AtomicInteger |
| `infra/exception/ToolSafetyException.java` | RuntimeException | ~50 | 含 7 种 Reason 枚举 |

### 4.2 5 个 file tool 接入

每个 tool 模式相同：

```diff
+ private final SandboxFactory sandboxFactory;
+ private final ToolCallCounter toolCallCounter;
+ public FileXxxTool(SandboxFactory sandboxFactory, ToolCallCounter toolCallCounter) { ... }

  @Tool("...")
  public String foo(String relativeFilePath, ..., @ToolMemoryId Long appId) {
      try {
-         Path path = Paths.get(relativeFilePath);
-         if (!path.isAbsolute()) {
-             String projectDirName = "vue_project_" + appId;
-             Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
-             path = projectRoot.resolve(relativeFilePath);
-         }
+         Sandbox sandbox = sandboxFactory.forVueApp(appId);
+         Path path = sandbox.resolveForXxx(relativeFilePath);  // Layer 1 + 2
+         toolCallCounter.recordCall(appId, relativeFilePath, ToolKind.XXX);  // Layer 3
          // ... 业务逻辑不变 ...
+     } catch (ToolSafetyException e) {
+         log.warn("[Safety] xxx rejected: {}", e.getMessage());
+         return "操作被拒绝（安全限制）: " + e.getReason() + " - " + relativeFilePath;
      } catch (IOException e) { ... }
  }
```

特殊处理：
- **FileDeleteTool**：用 `resolveForDelete` 自动校验关键文件黑名单；删除 18 行 `isImportantFile` 旧白名单方法
- **FileDirReadTool**：空 `relativeDirPath` 替换为 `"."` 表示工作目录本身（PathValidator 接受单 `.` 段）
- **ExitTool**：无路径参数，不接入 safety

### 4.3 测试覆盖（27/27 通过）

| 测试类 | @Test # | 覆盖 |
| --- | --- | --- |
| `PathValidatorTest` | 8 | null/空 / POSIX 绝对 / `~` / Windows 绝对 / `..` / 反斜杠 `\..\` / null byte / 控制字符 / 合法路径 |
| `SandboxTest` | 12 | 正常 resolve / `..` / 绝对 / 已存在目标 read / **POSIX 软链接逃逸 (DisabledOnOs Windows)** / 软链接祖先 / 删除关键文件 / 写入关键文件允许 / 全部 8 项关键文件 / 普通文件删除 / 嵌套路径 / workDir 暴露 |
| `ToolCallCounterTest` | 7 | 累计正常 / session 超限 / 单文件超限 / 多 appId 隔离 / reset / null 路径 / **10 线程并发安全** |

CI 命令：`mvn test -Dtest='com.prompt2app.agent.tools.safety.*Test'`

## 5. 验证

### 通用 DoD

- [x] 该 Phase 的 ADR 已写并 Accepted（ADR-0004）
- [x] Task Record 已留存（本文件）
- [x] `current-phase.md` Phase 3 全部 [x]
- [x] `milestones.md` Phase 3 切到 Done
- [x] commit message 标注 Phase
- [x] 没有遗留 `// TODO` 在主路径代码

### 任务专属验证（已实测）

- [x] **mvn clean compile 成功**：BUILD SUCCESS
- [x] **safety + evaluator 测试 36/36 通过**：
  ```
  PathValidatorTest:    8/8 passed in 0.006s
  SandboxTest:         12/12 passed in 0.048s
  ToolCallCounterTest:  7/7 passed in 0.120s
  EvalCaseLoaderTest:   3/3
  RubricScorerTest:     5/5
  EvalRunnerSmokeTest:  1/1
  Total:               36/36 passed
  ```
- [x] **5 个 file tool 全部接入**：构造器都注入 SandboxFactory + ToolCallCounter
- [x] **关键漏洞已闭合**：实测 `relativeFilePath = "../../etc/passwd"` 现在抛 `PATH_TRAVERSAL`
- [x] **关键文件保护升级**：从"按文件名 equalsIgnoreCase"升级为"按完整相对路径 Set 匹配"

## 6. 风险与遗留

### 已知风险

- **软链接测试 CI 平台依赖**：仅在 POSIX (Linux/macOS) 跑，Windows 跳过。Phase 5 引入 jimfs 时可统一。
- **阈值是默认值**（50 / 10）：未经真实使用调优，可能误杀长项目。可在 `application.yml` 覆盖。
- **AppConstant.CODE_OUTPUT_ROOT_DIR import 仍保留**：5 个 tool 的 import 已删 `infra.constant.AppConstant`，但 `SandboxFactory` 内部用了。这是预期内的变化。

### 遗留事项

- 把"接入到非 file tool"留给未来：当前 ExitTool 不计数，未来如新增需要计数的工具，要主动接入 ToolCallCounter
- ArchUnit 守护"任何 tool 必须经过 sandbox"延后到 Phase 5 评测体系自动化时一并加
- 24 个 SpringBootTest 集成测试依旧未修，沿用 backlog 项

## 7. 对治理体系的更新

- [x] 写了 ADR-0004（架构性变更必有 ADR）
- [x] 更新了 `docs/architecture/agent-design.md` 从 stub 到 v1（活文档跟代码同步）
- [x] 更新了 `docs/roadmap/current-phase.md`：Phase 3 全部 [x]，状态切换到 ✅ Done
- [x] 更新了 `docs/roadmap/milestones.md`：Phase 3 行 + 2 条状态变更
- [x] 没有需要新增 backlog（无意外发现）

## 8. 简历素材（面试爆点 #1）

> **Tool Calling Agent 三层安全防御（com.prompt2app.agent.tools.safety）**
> 设计 Schema 校验 + 工作目录沙箱 + 调用次数熔断三层纵深防御，沉淀为可复用的 5 个核心类（PathValidator / Sandbox / SandboxFactory / ToolCallCounter / ToolSafetyException）。覆盖 7 种攻击/异常场景：路径穿越 / 绝对路径 / 软链接逃逸 / 关键文件保护 / 死循环熔断 / 高频修改 / 并发安全。**27 单元测试**进 CI，包含 POSIX 软链接逃逸的 conditional test 和 10 线程并发安全验证。

可独立讲 5 分钟的子点：
- "为什么不用 Docker 沙箱？" → 作品集场景过度，capability scoping 已够（ADR-0004）
- "为什么三层而不是一层？" → 防御纵深（defense-in-depth）：Layer 1 漏一个 Layer 2 兜，Layer 3 限爆炸半径
- "怎么测软链接逃逸？" → JUnit 5 `@DisabledOnOs(WINDOWS)` + `Files.createSymbolicLink` 真实创建
- "为什么用 RuntimeException？" → LangChain4j tool framework 把异常转换为 LLM observation，让模型自我纠正

## 9. 下一步建议

按治理纪律：

1. **commit + push**：单 commit `feat(phase-3): tool calling 3-layer safety + 27 tests (ADR-0004)`
2. **Phase 3 关闭**：等用户决定何时启动 Phase 4（**面试爆点 #2 · AI Router 重写**）
3. **不要顺手开始 Phase 4**：Phase 4 是 4d 工时，需要新规划

---

**Phase 3 总览**：

```
2026-06-18 一天内完成（接续 Phase 0/1/2 同日）：
├── ADR-0004 起草（5 个实测漏洞 + 4 备选方案 + 三层设计 + 测试规划）
├── 5 个 main 类（safety 包 + 异常类）共 ~410 行
├── 5 个 file tool 接入（FileDeleteTool 还简化了 isImportantFile 旧逻辑）
├── 3 个测试类 27 @Test（含 POSIX 软链接 + 并发安全）
├── docs/architecture/agent-design.md 升级到 v1
└── 验证：mvn compile + safety/evaluator 36/36 通过

总产出：
- 1 个 commit（待）
- ADR-0004（实测漏洞驱动 + 三层 + 复盘指标）
- agent-design.md v1（活文档）
- 1 篇 task record
- 410 行 safety 代码 + 200 行测试代码
- 7 种攻击场景全部 CI 守护
```

> Phase 3 是治理纪律 + 工程化 + 安全意识三者交汇——简历**第一个能聊 5 分钟**的爆点正式落地。
