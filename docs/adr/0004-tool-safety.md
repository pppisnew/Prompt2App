# ADR-0004：Tool Calling 三层安全防御体系

- **状态**：Accepted
- **日期**：2026-06-18
- **决策者**：项目作者
- **相关 Phase**：Phase 3 · Tool 安全体系
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

项目使用 LangChain4j Tool Calling 让 AI Agent 操作文件系统（写 / 读 / 编辑 / 删除）。当前 6 个文件 tool 全部存在**未防护的路径越权漏洞**：

### 实测漏洞演示

```java
// FileWriteTool.writeFile 当前实现（精简）
Path path = Paths.get(relativeFilePath);
if (!path.isAbsolute()) {
    path = Paths.get(CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId).resolve(relativeFilePath);
}
Files.write(path, content);
```

**漏洞 #1**：`relativeFilePath = "../../etc/passwd"`
- `Paths.get(...).isAbsolute()` 返回 `false`（因为 `..` 路径不是绝对路径）
- 进入 if 分支后 `projectRoot.resolve("../../etc/passwd")` → 解析到工作目录之外
- `Files.write(...)` 真的写到 `/etc/passwd`（如果有权限），或抛权限错（不是安全错误！）

**漏洞 #2**：`relativeFilePath = "/tmp/evil.sh"`（绝对路径）
- `path.isAbsolute()` 返回 `true`，跳过相对路径分支
- 但 `path` 仍然是 `/tmp/evil.sh`，直接写入

**漏洞 #3**（FileDeleteTool）：`isImportantFile(fileName)` 只校验**文件名**，不校验路径
- `package.json` 在 `vue_project_1` 受保护
- `secret/package.json` 在任意目录均受保护（但 `secret/foo.json` 不受保护）
- 文件名正则可被绕过：`Package.json`（大小写）/ `package_json` 等

**漏洞 #4**：无单会话调用次数上限
- AI 出现死循环（"我修改文件 → 检查 → 不对再改"）可以无限调用
- 单 prompt 可能产生 1000+ tool call，烧光 Token + 占用 FS

**漏洞 #5**：无软链接（symlink）检测
- AI 可以 write 一个软链接 → workdir 内的文件指向 `/etc/passwd` → 后续 read/edit 通过软链接逃逸

### 事实判断

- **作品集场景**这些漏洞被攻击概率极低（用户是自己），但**面试场景必问**："如果用户输入 prompt 让 AI 删根目录怎么办？"
- **Phase 0 ADR-0008 已规划**："Tool 安全 TDD（路径越权 / 关键文件保护）"
- 治理体系（CI 红线 + DoD）要求 Phase 3 必须有 20+ 单测进 CI

---

## 备选方案（Options）

### 方案 A：单一防御（仅路径校验）

- **做法**：在每个 tool 入口加 `validatePath(relativeFilePath)`
- **优点**：实现简单，5 行代码搞定
- **缺点**：
  - 没法防软链接（运行时才能发现的 canonical path）
  - 没法防"AI 死循环烧 Token"
  - 关键文件保护需要单独逻辑
- **长期成本**：每加一个新 tool 都要复制粘贴校验代码

### 方案 B：三层防御（**本决策**）

| 层 | 时机 | 内容 |
| --- | --- | --- |
| Layer 1 · Schema 校验 | 入口（参数解析后立即） | 拒绝 `..` / 绝对路径 / null byte / 控制字符 / 空串 |
| Layer 2 · 能力沙箱 | 工具实际执行前 | 用 canonical path resolve；resolve 后必须 startsWith 工作目录；关键文件按 **完整相对路径** 黑名单（不只是文件名） |
| Layer 3 · 行为熔断 | 跨调用维度（按 appId） | 单会话工具调用次数上限 / 同文件高频修改告警 |

每层都有独立单测；Layer 1/2 在 tool 入口集中调用，Layer 3 由 ToolCallCounter 维护。

- **优点**：
  - 防御纵深：Layer 1 漏一个，Layer 2 兜底；Layer 2 漏一个，Layer 3 限制爆炸半径
  - 测试可独立：每层都能独立 unit test，不需要起 Spring 上下文
  - 面试可独立讲：每层一个故事点
- **缺点**：
  - 三个类 + 一个异常类 + 配置常量，初看 over-engineering
  - 现有 6 个 tool 需要逐个接入（机械工作）

### 方案 C：把 tool 放进 Docker 沙箱

- **做法**：每个 appId 的 tool 调用在独立容器里执行
- **优点**：终极隔离，可信
- **缺点**：
  - 引入 Docker 依赖（Charter §3 「明确不做：Docker per-app 沙箱」）
  - 与作品集目标不符
- **长期成本**：极高

### 方案 D：保持现状，写 ADR 说明"作品集场景不需要"

- **做法**：什么都不做，写一篇"承认漏洞 + 不修"的 ADR
- **缺点**：
  - 面试时被问到"为什么知道有漏洞还不修？"无法体面回答
  - 治理体系（DoD）要求 Phase 3 必有 20+ 单测
- **长期成本**：高（简历减分）

---

## 决策（Decision）

**选择方案 B**：三层防御 + 20+ 单测进 CI。

### 实施细节

#### Layer 1 · `PathValidator`

`agent/tools/safety/PathValidator.java`，纯函数类：

```java
public final class PathValidator {
    public static void validateRelative(String path) {
        // 拒绝：null / 空串 / 含 null byte / 含控制字符
        // 拒绝：绝对路径（startsWith /, 或符合 \\?\C:\... Windows pattern）
        // 拒绝：以 ~ 开头
        // 拒绝：任一段 == ".."
        // 接受：其它（合法相对路径）
    }
}
```

无依赖、无 Spring 上下文，纯 unit test 友好。

#### Layer 2 · `Sandbox`

`agent/tools/safety/Sandbox.java`，构造时绑定一个 workdir：

```java
public final class Sandbox {
    private final Path workDir;          // canonical
    private final Set<String> guardedRelPaths;  // ["package.json", "package-lock.json", "node_modules", ...]

    public Path resolve(String relativePath) throws ToolSafetyException {
        // 1. validateRelative（调用 Layer 1）
        // 2. workDir.resolve(relativePath).toRealPath() 或 normalize
        // 3. resolved.startsWith(workDir) 否则 throw
        // 4. 如果是 deletion-class 操作，再校验 guardedRelPaths
    }
}
```

每个 tool 通过 `new Sandbox(projectRoot)` 取得专属沙箱实例。

#### Layer 3 · `ToolCallCounter`

`agent/tools/safety/ToolCallCounter.java`，进程内单例（`@Component`）：

```java
@Component
public class ToolCallCounter {
    private final Map<Long, Counters> perApp = new ConcurrentHashMap<>();
    static class Counters {
        AtomicInteger total = new AtomicInteger();
        Map<String, AtomicInteger> perFile = new ConcurrentHashMap<>();
    }

    public void recordCall(Long appId, String relativePath, ToolKind kind) throws ToolSafetyException {
        // total++; if total > MAX_TOTAL throw CIRCUIT_BREAKER
        // perFile[path]++; if > MAX_PER_FILE throw HIGH_FREQ_MOD
    }

    public void reset(Long appId) { ... }
}
```

阈值默认值（在 `infra/constant/AppConstant`）：
- `TOOL_CALL_MAX_PER_SESSION = 50`
- `TOOL_CALL_MAX_PER_FILE = 10`

#### `ToolSafetyException`

`infra/exception/ToolSafetyException.java`（runtime exception，含 `Reason` 枚举：`PATH_TRAVERSAL / PATH_ABSOLUTE / PATH_INVALID / GUARDED_FILE / SYMLINK_ESCAPE / CIRCUIT_BREAKER / HIGH_FREQ_MOD`）。

LangChain4j tool 抛出 RuntimeException 会被框架捕获并作为 tool 执行错误回报给 LLM——LLM 可以收到错误消息后调整策略。

#### 接入到现有 6 个 tool

每个 file tool 在入口替换原 `Paths.get(...)` 逻辑：

```java
// 旧
Path path = Paths.get(relativeFilePath);
if (!path.isAbsolute()) {
    path = projectRoot.resolve(relativeFilePath);
}

// 新
Sandbox sandbox = sandboxFactory.forApp(appId);          // 注入
Path path = sandbox.resolveForWrite(relativeFilePath);   // Layer 1+2
toolCallCounter.recordCall(appId, relativeFilePath, ToolKind.WRITE);  // Layer 3
```

#### 单测覆盖（≥20 条，目标 ≥24 条）

| 测试类 | 测试 # | 覆盖 |
| --- | --- | --- |
| `PathValidatorTest` | 8 | null / 空 / 绝对 / `..` / `~` / null byte / 控制字符 / 合法 |
| `SandboxTest` | 10 | resolve 正常 / resolve `..` / resolve 绝对 / canonical 越权 / 软链接逃逸 / 关键文件删除 / 关键文件写入允许 / 多级路径 / 不存在的子目录 / 大小写 |
| `ToolCallCounterTest` | 6 | 正常累计 / total 超限 / perFile 超限 / 多 appId 隔离 / reset 行为 / 并发安全 |

CI: `mvn test -Dtest='com.prompt2app.agent.tools.safety.*Test'` 必须 100% 通过。

---

## 代价（Consequences）

### 正面

- **真实修复 5 个安全漏洞**：路径越权 / 绝对路径 / 关键文件 / 软链接 / 调用循环
- **20+ 单测**沉淀为面试可量化的"工程化"证据
- 后续新加 tool 只需调用 `Sandbox + ToolCallCounter`，安全是"通过设计获得"而非"逐工具复制粘贴"
- 简历可讲："Agent 安全的本质是 capability scoping。我把每个 tool 的能力收敛到 appId 命名空间内，加调用次数熔断。CI 跑 24 个攻击用例。"

### 负面

- **现有 6 个 tool 都要改**：约 50-80 行机械修改
- **Sandbox 引入 IO**（`toRealPath` 检查软链接需要文件存在）：对"写入新文件"的 case 需要降级用 `normalize` + startsWith 检查（无 IO）
- **测试 ≥ 20 条**编写需要 1-2 小时；macOS/Linux 软链接行为差异需 conditional skip

### 中性

- LangChain4j RuntimeException → LLM tool error message 的传递语义未来可能调优（目前直接抛即可）
- Spring Bean 注入 `Sandbox` 还是 `SandboxFactory`：选 Factory 模式（每个 tool 调用 `forApp(appId)` 获取实例），避免在 Bean 上挂 appId 状态

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估：

- 出现已知 attack pattern 绕过三层防御 → 加第 4 层
- 单会话调用 50 次仍不够（合法长项目场景） → 调高阈值或引入"白名单工具不计数"
- 软链接 case 在 CI 一直 flaky → 改为 mock 文件系统（jimfs / memfs）
- LangChain4j 升级后 RuntimeException 的传递机制变化 → 重审 ToolSafetyException 设计

---

## 关于"为什么 Tool Calling 而非一次性生成"

> 本 ADR 的副产物。Phase 3 名义上是"Tool 安全"，实际隐含一个上游决策：选择 Tool Calling 范式而非一次性生成。

**选择 Tool Calling 的理由**：

| 维度 | 一次性生成 | Tool Calling |
| --- | --- | --- |
| 简单 prompt | ✅ 快、便宜 | 浪费 round-trip |
| 复杂多文件项目（Vue） | ❌ 单次输出 token 上限 / 一处错全篇错 | ✅ 步骤可观测、可纠正 |
| 用户体验 | 一次性大 chunk 落地 | SSE 流式工具事件，"AI 在动"反馈强 |
| 安全爆炸半径 | 全输出一次落盘 | 单 tool 单次 effect |

**实际策略**（与 Phase 4 路由对应）：

- HTML 短 prompt → 一次性生成（不走 tool）
- MultiFile → 一次性生成 + 多文件解析
- Vue 复杂应用 → Tool Calling Agent（**本 Phase 安全的针对对象**）

这条选择 Phase 4（ADR-0003）会再细化，本 ADR 仅作为前提交代。

---

## 参考资料（References）

- [`docs/adr/0008-evaluation-first.md`](./0008-evaluation-first.md) §备选方案：Phase 3 起手已规划
- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §2 「核心能力 P0：Tool Calling Agent + 三层安全」
- [`docs/architecture/agent-design.md`](../architecture/agent-design.md) ← 本 ADR 落地后从 stub 升级
- 业界参考：[OpenAI Function Calling Best Practices](https://platform.openai.com/docs/guides/function-calling)、[Anthropic Tool Use Safety](https://docs.anthropic.com/en/docs/agents-and-tools/tool-use)
