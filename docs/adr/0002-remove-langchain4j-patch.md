# ADR-0002：升级 LangChain4j 1.1.0 → 1.5.1 并删除源码覆盖 patch

- **状态**：Accepted
- **日期**：2026-06-18
- **决策者**：项目作者
- **相关 Phase**：Phase 2 · 删 LangChain4j Patch
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

教学版项目在 commit `893918c` 引入了一组 LangChain4j 源码覆盖文件：

```
src/main/java/dev/langchain4j/
├── internal/ToolExecutionRequestBuilder.java                    98 行
├── model/chat/StreamingChatModel.java                          117
├── model/chat/response/StreamingChatResponseHandler.java        61
├── model/openai/OpenAiStreamingChatModel.java                  432
├── model/openai/OpenAiStreamingResponseBuilder.java            236
├── service/AiServiceStreamingResponseHandler.java              231
├── service/AiServiceTokenStream.java                           192
└── service/TokenStream.java                                     83
                                                            ─────────
                                                              1450 行
```

它们与三方包 `dev.langchain4j` 同包名，通过 Java 类路径优先规则（同包同类名时，src/main/java 下的文件优先于 jar 中的）覆盖了 LangChain4j 1.1.0 的官方实现，目的是为 `TokenStream` 添加 `onPartialToolExecutionRequest(...)` 方法（流式工具事件回调）。

**事实判断**：

- **唯一业务调用点**：`agent/codegen/AiCodeGeneratorFacade.java:118` 调用 `.onPartialToolExecutionRequest(...)`
- **patch 是绕开当时 1.1.0 限制的临时方案**：1.5+ 系列原生支持该回调
- **风险 #1 升级即死**：LangChain4j 任何 minor 升级都会让 patch 与上游签名错位，编译断裂
- **风险 #2 行为不一致**：patch 维护成本被项目作者承担，错过上游 bug fix
- **风险 #3 简历减分项**：面试官追问"为什么 patch？""现在还需要吗？" 是必爆点

ADR-0008 已在 task record 中明确："升级 LangChain4j 1.5+，原生已支持 `onPartialToolExecutionRequest`"。本 ADR 把这一计划落地。

### 现有依赖快照

```xml
<!-- pom.xml 当前 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.1.0</version>
</dependency>
<dependency>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.1.0-beta7</version>
</dependency>
<dependency>
    <artifactId>langchain4j-reactor</artifactId>
    <version>1.1.0-beta7</version>
</dependency>
<dependency>
    <artifactId>langchain4j-community-redis-spring-boot-starter</artifactId>
    <version>1.1.0-beta7</version>
</dependency>
```

### Maven 中央仓库可用版本（截至 2026-06-18）

| Artifact | 当前 | 1.5.x 可用 | 最新 |
| --- | --- | --- | --- |
| `langchain4j` | 1.1.0 | **1.5.1** ✅ | 1.16.3 |
| `langchain4j-reactor` | 1.1.0-beta7 | 1.5.1 (BOM) | 1.16.3 |
| `langchain4j-open-ai-spring-boot-starter` | 1.1.0-beta7 | **1.5.1-beta11** ✅ | 1.16.3-beta26 |
| `langchain4j-community-redis-spring-boot-starter` | 1.1.0-beta7 | **1.5.0-beta11** ✅（无 1.5.1）| 1.16.0-beta26 |
| `langchain4j-bom` | 未使用 | **1.5.1** ✅ | 1.16.3 |

---

## 备选方案（Options）

### 方案 A：保留 patch，不升级

- 优点：零风险
- 缺点：见上文「风险 #1-#3」
- 长期成本：每次想升级都被 patch 卡住

### 方案 B：升级到 1.5.1（**本决策**）

- ADR-0008 task record 中已规划的版本
- 1.5.1 vs 1.1.0 跨 4 个 minor，但 LangChain4j 1.x 系列 API 稳定（小步迭代）
- BOM 机制可统一版本号，减少手工对齐成本
- `onPartialToolExecutionRequest` 在 1.5+ 已是公开 API
- 子包：开 BOM 拉齐核心；spring-boot-starter 用 `1.5.1-beta11`；community-redis 用 `1.5.0-beta11`（无 1.5.1 版本）

### 方案 C：直接升级到 1.16.3（最新）

- 优点：一次到位
- 缺点：
  - 1.5 → 1.16 跨 11 个 minor，breaking changes 累积更多
  - ADR-0008 当初规划是 1.5+，超出该规划需要先改 ADR
  - 作品集场景不需要持续追新——稳定就好
- 长期成本：测试负担大

### 方案 D：保留 patch + 仅升级三方包到 1.5.x

- 完全不可行：patch 同包名覆盖三方类，三方升级后 patch 就要同步改，否则编译断裂

---

## 决策（Decision）

**选择方案 B**：升级到 LangChain4j 1.5.1（含相应子包），删除全部 patch 文件。

### 实施细节

1. **引入 `langchain4j-bom` 1.5.1** 到 `<dependencyManagement>`，统一管理核心 + reactor 等版本
2. **保留显式 version**（不依赖 BOM）的子包：
   - `langchain4j-open-ai-spring-boot-starter` → `1.5.1-beta11`（spring-boot-starter 始终带 -beta）
   - `langchain4j-community-redis-spring-boot-starter` → `1.5.0-beta11`（无 1.5.1 版本，回退到 1.5.0）
3. **`<dependency>` 块清理**：移除 `langchain4j` / `langchain4j-reactor` 显式 `<version>`，由 BOM 注入
4. **删除 `src/main/java/dev/langchain4j/` 整目录**（8 文件）
5. **业务代码改动**：1 处必需适配（实施时发现，详见下文）

### 实施时的发现：onPartialToolExecutionRequest 在 1.5.1 不存在

ADR 起草时基于 ADR-0008 task record 的判断："1.5+ 原生支持 onPartialToolExecutionRequest"。**实测后这个判断不准确**：

LangChain4j 1.5.1 `TokenStream` 接口实际暴露的 tool 相关方法：
- `beforeToolExecution(Consumer<BeforeToolExecution>)` — 工具执行前一次性回调（含完整请求）
- `onToolExecuted(Consumer<ToolExecution>)` — 工具执行后回调
- `onPartialResponse / onPartialThinking / onCompleteResponse / onError` — 文本/思考/完成/错误

**没有** `onPartialToolExecutionRequest`（即"工具调用参数流式增量"）方法。该方法名在 LangChain4j 1.x 系列中**从未存在于公开 API**——patch 文件本身就是教学版作者的私有扩展。

### 适配方案

把 `agent/codegen/AiCodeGeneratorFacade.java:118` 的回调从 `onPartialToolExecutionRequest((index, req) -> ...)` 改为 `beforeToolExecution(beforeToolExec -> new ToolRequestMessage(beforeToolExec.request()))`：

```diff
- .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
-     ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
+ .beforeToolExecution((BeforeToolExecution beforeToolExec) -> {
+     ToolRequestMessage toolRequestMessage = new ToolRequestMessage(beforeToolExec.request());
      sink.next(JSONUtil.toJsonStr(toolRequestMessage));
  })
```

**行为差异**：
- **patch 旧行为**：tool 调用参数 JSON 流式增量推送（多次回调）
- **1.5.1 新行为**：tool 调用就绪一次性推送（单次回调）

**对 SSE UX 的影响**：极小。前端原本依赖 `ToolRequestMessage` 在 SSE 流里展示"AI 决定调用工具 X"，新方案仍发送一次该消息，只是不再有"打字机"式参数流式效果。作品集场景可接受。

如未来需要恢复"参数流式"效果（例如展示长 JSON 慢慢生成的体验），有两条路：
- 升级到 1.7+ 并查看是否有更细粒度的 callback（届时新 ADR 评估）
- 在 `beforeToolExecution` 单次回调里手动按字符切分推送（应用层伪流式）

### 验证手段

- `mvn compile` 通过（含全部业务包）
- `mvn test -Dtest='com.prompt2app.eval.*Test'` 9/9 通过（与 Phase 1 baseline 持平）
- 手工搜索：`grep -r 'dev/langchain4j' src/` 返回 0 行

---

## 代价（Consequences）

### 正面

- **彻底消除"为什么有 patch"这个面试爆点**
- 后续 Phase 3-7 可以基于稳定的 LangChain4j API 推进
- pom.xml 可读性提升（BOM 机制让版本管理清晰）
- patch 1450 行从 git history 永久消失（commit 893918c 仍可访问，作为历史存档）

### 负面

- **跨 4 个 minor 的 API 变化风险**：1.1 → 1.5 之间 LangChain4j 可能调整方法签名 / 包路径。预期影响最小（核心 API 1.x 系列稳定），但需 mvn compile 实测兜底
- **Spring Boot Starter 仍是 beta**：`-beta11` 不是稳定版。但与原项目 `1.1.0-beta7` 等同程度，无升级
- **community-redis 用 1.5.0-beta11**：与核心 1.5.1 不严格对齐（差一个 patch 版）。LangChain4j 社区包发布节奏独立，业界常态

### 中性

- 升级到 1.5.1 后，未来如果想跳到 1.10+ 或 1.16+ 系列，仍需要新的 ADR 评估 breaking changes
- LangChain4j 升级**不**等于 LangGraph4j 升级（后者在 `agent/workflow/`，命运待 ADR-0007）

### 实测验证（2026-06-18 完成）

- ✅ `mvn clean compile -DskipTests` 成功，**180 class** 产出（patch 删除前 191 → 减 11 = 8 patch + 3 lambda 内部类）
- ✅ `mvn test -Dtest='com.prompt2app.eval.*Test'` 9/9 通过，与 Phase 1 baseline 持平
- ✅ `grep -r 'dev/langchain4j' src/` 返回 0 行
- ✅ `find src/main/java/dev` 不存在（目录已 rmdir）

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估本决策：

- LangChain4j 1.5.x 出现已知严重 bug 且 1.5.1 不修
- 后续 Phase 需要 1.6+ 才有的 API（届时新写 ADR-0002b 升级）
- Spring Boot Starter beta 行为与 stable core 不一致导致运行时怪异
- 项目想用 LangChain4j 多模型 Provider（除 OpenAI 外的接入），可能需要更新 starter

---

## 关于 patch 删除的元规则

> 本项目治理体系的核心：**所有越界、删除、重命名都必须有 ADR**。
> ADR-0009 是"项目重命名"的越界批准；本 ADR 是"删除三方库覆盖"的正式记录。
> 未来 AI 助手如果发现项目里有"看起来奇怪"的 dev/foo/ 类型源码覆盖，**先停下来读 ADR**，再决定动不动。

---

## 参考资料（References）

- [`docs/adr/0008-evaluation-first.md`](./0008-evaluation-first.md) §备选方案：本决策的最初规划点
- [`docs/reverse-engineering/项目逆向工程与Vibe-Coding复现指南.md`](../reverse-engineering/项目逆向工程与Vibe-Coding复现指南.md) §架构决策记录 ADR：列出 patch 是 ① 致命问题之一
- 业务代码调用点：`src/main/java/com/prompt2app/agent/codegen/AiCodeGeneratorFacade.java:118`
- LangChain4j 1.5.0 release notes：[`onPartialToolExecutionRequest` 原生支持](https://docs.langchain4j.dev/tutorials/ai-services#streaming)
- 历史存档：git tag `microservice-final` (commit 893918c) 包含 1.1.0 + patch 的完整状态
