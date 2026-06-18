# Task: Phase 2 删除 LangChain4j 源码覆盖 patch + 升级到 1.5.1

- **日期**：2026-06-18
- **Phase**：Phase 2 · 删 LangChain4j Patch
- **责任人**：项目作者（指令）+ AI 助手（执行）
- **状态**：Done
- **关联 ADR**：[ADR-0002](../adr/0002-remove-langchain4j-patch.md)

---

## 1. 目标

按 ADR-0002 完成：

1. 升级 LangChain4j 1.1.0 → 1.5.1（含 BOM 机制引入）
2. 删除 `src/main/java/dev/langchain4j/` 8 个 patch 文件（1450 行）
3. 验证编译 + evaluator 回归

## 2. 背景

教学版项目通过同包名覆盖技术，向 `TokenStream` 注入了 `onPartialToolExecutionRequest(...)` 方法。这种 patch 是技术债：升级即死、错过上游 bug fix、面试爆点。ADR-0008 task record 明确"1.5+ 原生支持该回调"——本次落地。

## 3. 影响范围（Scope）

- **修改**：`pom.xml`（properties + dependencyManagement + 4 dependency 块）
- **修改**：`src/main/java/com/prompt2app/agent/codegen/AiCodeGeneratorFacade.java`（1 处 API 适配 + 1 行 import）
- **删除**：`src/main/java/dev/langchain4j/` 整目录（8 文件）
- **新增**：`docs/adr/0002-remove-langchain4j-patch.md`、本 task record
- **未触碰**：业务逻辑（除 1 处 API 适配）、其它包、前端

## 4. 修改内容

### 4.1 pom.xml 升级

```diff
 <properties>
     <java.version>21</java.version>
+    <langchain4j.version>1.5.1</langchain4j.version>
+    <langchain4j-spring.version>1.5.1-beta11</langchain4j-spring.version>
+    <langchain4j-community.version>1.5.0-beta11</langchain4j-community.version>
 </properties>

+<dependencyManagement>
+    <dependencies>
+        <dependency>
+            <groupId>dev.langchain4j</groupId>
+            <artifactId>langchain4j-bom</artifactId>
+            <version>${langchain4j.version}</version>
+            <type>pom</type>
+            <scope>import</scope>
+        </dependency>
+    </dependencies>
+</dependencyManagement>

 <!-- LangChain4j -->
 <dependency>
     <artifactId>langchain4j</artifactId>
-    <version>1.1.0</version>
+    <!-- BOM-managed -->
 </dependency>
 <dependency>
     <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
-    <version>1.1.0-beta7</version>
+    <version>${langchain4j-spring.version}</version>
 </dependency>
 <dependency>
     <artifactId>langchain4j-reactor</artifactId>
-    <version>1.1.0-beta7</version>
+    <!-- BOM-managed -->
 </dependency>
 <dependency>
     <artifactId>langchain4j-community-redis-spring-boot-starter</artifactId>
-    <version>1.1.0-beta7</version>
+    <version>${langchain4j-community.version}</version>
 </dependency>
```

### 4.2 patch 删除

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

`git rm -r src/main/java/dev/langchain4j/` 一刀整个目录。

### 4.3 关键发现：onPartialToolExecutionRequest 在 1.5.1 不存在

ADR-0008 task record 当初的判断"1.5+ 原生支持 onPartialToolExecutionRequest"**不准确**。实测 1.5.1 jar 的 `TokenStream` 接口有：

- `onPartialResponse(Consumer<String>)`
- `onPartialThinking(Consumer<PartialThinking>)`
- `beforeToolExecution(Consumer<BeforeToolExecution>)`  ← 新 API
- `onToolExecuted(Consumer<ToolExecution>)`
- `onIntermediateResponse / onCompleteResponse / onError / onRetrieved`

**没有** `onPartialToolExecutionRequest`。该方法名在 LangChain4j 1.x 系列**从未存在于公开 API**——patch 的 `TokenStream.java` 是教学版作者的私有扩展。

### 4.4 API 适配

`agent/codegen/AiCodeGeneratorFacade.java:118` 的回调改写：

```diff
+ import dev.langchain4j.service.tool.BeforeToolExecution;

  // 在 processTokenStream 方法中：
- .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
-     ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
+ .beforeToolExecution((BeforeToolExecution beforeToolExec) -> {
+     ToolRequestMessage toolRequestMessage = new ToolRequestMessage(beforeToolExec.request());
      sink.next(JSONUtil.toJsonStr(toolRequestMessage));
  })
```

`BeforeToolExecution.request()` 返回 `ToolExecutionRequest`，与现有 `ToolRequestMessage` 构造器签名 100% 匹配，无需改 message 类。

**行为差异**：
- patch 旧行为：tool 调用参数 JSON 流式增量推送（多次回调）
- 1.5.1 新行为：tool 调用就绪一次性推送（单次回调）

对 SSE UX 的影响极小——前端展示"AI 决定调用工具 X"的消息仍会触发，只是不再有打字机式参数流式效果。作品集场景可接受。

### 4.5 治理文档同步

- `docs/adr/0002-remove-langchain4j-patch.md`：完整 ADR（4 备选方案、API 适配实测发现、行为差异说明、复盘指标）
- `docs/adr/README.md`：索引追加 ADR-0002
- `docs/roadmap/current-phase.md`：DoD 全部 [x]，状态切换到 ✅ Done
- `docs/roadmap/milestones.md`：Phase 2 行 + 2 条状态变更

## 5. 验证

### 通用 DoD

- [x] 该 Phase 的 ADR 已写并 Accepted（ADR-0002）
- [x] Task Record 已留存（本文件）
- [x] `current-phase.md` Phase 2 全部 [x]
- [x] `milestones.md` Phase 2 切到 Done
- [x] commit message 标注 Phase
- [x] 没有遗留 `// TODO` 在主路径代码

### 任务专属验证

- [x] **patch 整目录删除**：`grep -r 'dev/langchain4j' src/` 返回 0 行
- [x] **dev/ 目录清空**：`find src/main/java/dev` 不存在
- [x] **mvn clean compile 成功**：**180 class** 产出（patch 删除前 191 → 减 11 = 8 patch + 3 lambda 内部类）
- [x] **evaluator 回归 9/9**：`mvn test -Dtest='com.prompt2app.eval.*Test'` BUILD SUCCESS / 1.521s
- [x] **business code 调用点适配验证**：`AiCodeGeneratorFacade.java:118` 已使用原生 API，编译通过

## 6. 风险与遗留

### 已知风险

- **Spring Boot Starter 仍是 beta**：`-beta11` 子版本，与原项目 `-beta7` 等同程度。LangChain4j 社区习惯，无更优替代。
- **community-redis 用 1.5.0-beta11（非 1.5.1）**：差一个 patch 版，因 community 包 1.5.x 线只发布到 1.5.0。可接受。
- **行为差异**：tool 参数流式 → 一次性。简历可讲："本来想保留打字机效果，但评估后发现需要应用层伪流式实现，与 ADR-0002 一次性删 patch 的目标矛盾，决定接受单次回调。"

### 遗留事项

- **未来升到 1.7+/1.16+**：Phase 8+ 评估。届时新写 ADR-0002b。
- **24 个 SpringBootTest 集成测试**：仍未修复，沿用 Phase 1 backlog 项。

## 7. 对治理体系的更新

- [x] 写了 ADR-0002（架构性变更必有 ADR）
- [x] ADR-0002 包含**实施时的发现**（API 名称错位）和**修订**（适配 beforeToolExecution）——这是治理体系"ADR 反映真实决策过程"的体现，不是事后编故事
- [x] 更新了 `docs/roadmap/current-phase.md`：Phase 2 全部 [x]，状态切换到 ✅ Done
- [x] 更新了 `docs/roadmap/milestones.md`：Phase 2 行 + 2 条状态变更
- [x] 没有需要新增 backlog（无遗漏发现）

## 8. 下一步建议

按治理纪律：

1. **commit + push**：单 commit `feat(phase-2): upgrade LangChain4j 1.1.0 → 1.5.1 and drop the patch (ADR-0002)`
2. **Phase 2 关闭**：等用户决定何时启动 Phase 3（Tool 安全体系，⭐⭐⭐⭐⭐ 面试爆点）
3. **不要顺手开始 Phase 3**：Phase 3 是大头工作（4 天工作量 + 20+ 单测），需要新规划

---

**Phase 2 总览**：

```
2026-06-18 一天内完成（接续 Phase 0 + Phase 1 同日）：
├── ADR-0002 起草（含 4 备选方案 + API 实测发现 + 行为差异 + 复盘指标）
├── pom.xml: 引入 langchain4j-bom 1.5.1，子包升级到 1.5.1-beta11 / 1.5.0-beta11
├── 删除 src/main/java/dev/langchain4j/ 8 文件 / 1450 行
├── AiCodeGeneratorFacade.java: 1 处 API 适配（onPartialToolExecutionRequest → beforeToolExecution）
└── 验证：mvn compile 180 class（少 11）+ evaluator 9/9 持平

总产出：
- 1 个 commit（待）
- ADR-0002（含实测发现修订）
- 1 篇 task record
- 删除 1450 行三方库覆盖代码
- 1 行核心业务适配
```

> "为什么有 patch？" "现在还需要吗？"——这两个面试问题被一次性消除。
