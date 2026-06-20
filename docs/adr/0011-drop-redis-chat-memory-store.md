# ADR-0011 删除 RedisChatMemoryStore：未生效的依赖应及时回收

- **状态**：Accepted
- **日期**：2026-06-19
- **决策人**：项目作者 + AI 协作者
- **关联 Phase**：Phase 8（增量 · 启动事故复盘第 3 个事件）
- **相关 ADR**：ADR-0010（配置统一化）触发了启动事故链；本 ADR 处置链上第三个故障
- **相关 Charter 条款**：§3「明确不做」之「克制添加」；§4「ADR 覆盖」

---

## 1. 背景

### 事故触发

Phase 8 的两次最小修复之后再次启动应用，请求处理流程抛：

```
redis.clients.jedis.exceptions.JedisDataException: ERR unknown command 'JSON.GET'
    at langchain4j-community-redis 的 RedisChatMemoryStore.getMessages
```

直接原因：`langchain4j-community-redis-spring-boot-starter` 的 `RedisChatMemoryStore` 实现使用 RedisJSON 模块的 `JSON.GET / JSON.SET / JSON.DEL` 命令，但当前部署的 vanilla Redis（Homebrew `redis-server`）未加载该模块。

### 代码审计揭示的更深层事实

阅读 `AiCodeGeneratorServiceFactory:90-100`：

```java
MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
    .id(appId)
    .chatMemoryStore(redisChatMemoryStore)   // 1. 配置 Redis 后备
    .maxMessages(20)
    .build();
chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);  // 2. MySQL 回放
```

`loadChatHistoryToMemory(...)` 内部（`ChatHistoryServiceImpl:94-114`）：

```java
chatMemory.clear();                           // 销毁 Redis 现有数据 (DEL/JSON.DEL)
for (var history : list) {
    chatMemory.add(...);                      // 从 MySQL 一条条重新写回 Redis
}
```

**结论**：每次 `serviceCache`（Caffeine）失效（重启 / 淘汰 / TTL 过期）后，Redis 里的内容会被 MySQL 回放完整覆盖。Redis 永远在做 `clear → re-fill from MySQL` 循环，**从不作为权威来源被读**。

它是一个永远不命中的缓存——每次冷启动都把缓存清空再用源数据重建。**不只是当前失败了，它从来没起过作用**。

### Redis 缓存层"值得存在"的三种场景及当前是否成立

| 场景 | 价值 | 当前是否成立？ |
| --- | --- | --- |
| **A. 多实例水平扩展 + 无 sticky session** | 实例 X 写、实例 Y 读，必须共享存储 | ❌ Charter §3 默认单实例；Phase 0-8 都是模块化单体 |
| **B. JVM heap 压力大，offload 到外部 store** | 每 chatMemory ≤ 20×5KB ≤ 100KB；并发 1k+ session 才有压力 | ❌ 作品集场景 100~1000 app 总量、同时 chat 极少 |
| **C. 冷启动加速（warm cache）** | 重启后跳过 MySQL 回放 | ❌ 回放 20 条 ≈ 5ms；Caffeine 之后 ≈ 0ms。提速空间可忽略 |

**三项均不成立**——即使把 Redis 层"修正确"，在当前架构下也提供不了实质价值。

---

## 2. 备选方案

### 方案 A：切 redis-stack（含 RedisJSON 模块）

让本地 Redis 换成 redis-stack-server，使 `JSON.GET` 能识别。

- 工作量：5 min（doc + brew install / docker）
- 代价：所有部署文档都得说明"必须使用 redis-stack 不是 vanilla redis"；增加运维心智负担；不消除冗余
- 评价：治标不治本——把一个不该存在的依赖伺候得更好

### 方案 B：让 Redis 成为权威 cache、MySQL 仅作 fallback（"修复方案 R"）

改 `loadChatHistoryToMemory(...)` 调用条件为 Redis miss 时才执行：

```java
if (chatMemory.messages().isEmpty()) {        // Redis miss
    chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
}
```

并需要：
- 仍然要求 redis-stack 才能跑（因为 `chatMemory.messages()` 触发 `JSON.GET`）
- 处理一致性问题：管理员在另一个进程改了 `chat_history` 表 → Redis 不会自动失效，要写 invalidation
- 增加 RedisJSON 模块作为生产环境硬依赖

- 工作量：1.5-2d（含一致性 / 失效设计 + 单测）
- 代价：增加运维依赖、增加缓存一致性维护成本
- 收益：在当前**单实例 + 低并发**前提下接近 0
- 评价：以工程成本去保留一个未被需要的能力；过早泛化

### 方案 C：删除 RedisChatMemoryStore（采纳）

`MessageWindowChatMemory.builder()` 不指定 `chatMemoryStore` → 用 LangChain4j 默认 in-process 实现。
`chatHistoryService.loadChatHistoryToMemory(...)` 仍然作为权威源每次回放（行为不变）。

- 工作量：30 min（删配置类 + 改 factory + 删依赖 + 测试 + 文档）
- 代价：失去**多实例下 chat memory 共享能力**（当前不需要）；退出 `langchain4j-community-redis-spring-boot-starter` 依赖
- 评价：**最符合 Charter §3「克制添加」精神**——不为未发生的需求保留依赖；现状本就如此，删除只是把代码与现实对齐

### 方案 D：自写 vanilla Redis ChatMemoryStore（基于 SET/GET + JSON 序列化）

实现 `ChatMemoryStore` 接口，手动 Jackson 序列化 `List<ChatMessage>`，用 `SET` / `GET` / `DEL` 替代 RedisJSON 命令。

- 工作量：4-6h（含单测）
- 代价：自维护一个序列化器与失败处理；测试覆盖；**和方案 B 一样未解决"是否需要 Redis 层"这个真问题**
- 评价：过度工程；只在确实需要 Redis 层但不能要 RedisJSON 时才合理（当前两条都不成立）

---

## 3. 决策

**采纳方案 C：删除 RedisChatMemoryStore**。

具体动作：
1. 删除 `infra/config/RedisChatMemoryStoreConfig.java`
2. `AiCodeGeneratorServiceFactory.java` 删除 `redisChatMemoryStore` `@Resource` 字段；`MessageWindowChatMemory.builder()` 链上去掉 `.chatMemoryStore(...)` 调用
3. `pom.xml` 删除 `<dependency>langchain4j-community-redis-spring-boot-starter</dependency>` （以及不再被任何代码引用的 `${langchain4j-community.version}` 属性如果只此一处用到，亦可顺手删）
4. **保留** `spring-session-data-redis` —— 它服务于 HTTP Session 存储，与 chat memory 无关，照常使用
5. **保留** `spring-data-redis` 通用栈与 `Redisson` —— 仍可能被 `@Cacheable` / 分布式锁等其他模块用到

### 决策的隐含前提（如失效则需重启 ADR）

1. **单实例部署**：Charter §3 默认；Phase 0-8 都遵守
2. **JVM heap 容量充足**：每 chatMemory ≤ 100KB，总 app session 数 < 数千
3. **MySQL `chat_history` SELECT LIMIT 20 性能可接受**：≤ 10ms

---

## 4. 代价（明确认定）

| 失去什么 | 是否影响当前业务 |
| --- | --- |
| 多实例 chat memory 共享 | 否（不存在该场景） |
| 应用重启时跳过 MySQL 回放的能力 | 否（回放成本 ≈ 5ms / 缓存命中后 0ms） |
| RedisJSON 高级数据结构能力 | 否（项目无其他地方用到） |
| `langchain4j-community-redis` 依赖（含其传递依赖） | jar 体积 ↓ |

---

## 5. 复盘指标

- 1 月内是否有用户反馈 "chat 记忆丢失" / "重启后历史消失"
- 单实例 JVM heap 是否随 app session 数线性显著增长
- AI 代码生成的 chat 上下文连续性是否受影响（人工抽样测）

---

## 6. 触发回头看的条件（未来路标，不是现在的工作）

任一前提崩塌时**开 ADR-0012 重新引入 Redis 层**：

| 触发条件 | 对应未来 ADR | 推荐方案 |
| --- | --- | --- |
| 引入水平扩展 / 多实例（k8s, 多副本） | ADR-0012-A「跨实例 chat memory 共享」 | 重启用 RedisChatMemoryStore（必装 redis-stack）或 PostgreSQL chat memory |
| chat 高并发使 JVM heap 出现压力 | ADR-0012-B「卸载 chatMemory 到外部 store」 | redis-stack 或自写 vanilla redis store |
| 大量 chat history 使 MySQL 回放变慢（>50ms） | ADR-0012-C「chat memory 缓存层」 | 加索引 / 加 Redis cache 层 |

> 这就是为什么"删除"不等于"永远不做"——决策连同它的边界条件一同写下，未来可以基于事实回滚而非凭直觉。

---

## 7. 与 Charter 的关系

- **Charter §3「明确不做」** 不显式包含 RedisChatMemoryStore，但隐含的"克制添加"原则在此体现：当一个组件未能提供宣称的价值，且当前规模下保留它需要花工程预算 → 删除并写下边界条件
- **Charter §4「ADR 覆盖」** 要求架构变更必须有 ADR：本 ADR 即此
- **Charter §6「修订流程」** 不需要触发：本 ADR 不修订 Charter，仅是其精神的一次具体应用

---

## 8. 参考

- 故障链上前两个事故：[`docs/tasks/2026-06-19-phase8-bootstrap-incident-fix.md`](../tasks/2026-06-19-phase8-bootstrap-incident-fix.md)
- ADR-0010（配置统一化）触发整条故障链，但本身决策不变
- LangChain4j 文档 · ChatMemoryStore 接口
- redis-stack 与 RedisJSON 模块官方说明（备查，本次不引）

---

## 9. 教训与回归修复（2026-06-20 增补）

### 9.1 自己引入的回归

ADR-0011 删除 `langchain4j-community-redis-spring-boot-starter` 时，**未核对传递依赖被谁使用**。该 starter 通过传递依赖提供了 `spring-boot-starter-data-redis`，而：

- `RedisCacheManagerConfig` 需要 `RedisConnectionFactory`（来自 spring-boot-starter-data-redis 的自动配置）
- `AppController` 使用 `@Cacheable`（同样需要 Spring Cache + Redis backend）
- `spring-session-data-redis` 仅声明 session API，**不**带连接池自动配置

删除 starter 后启动应用立刻抛：

```
Error creating bean with name 'redisCacheManagerConfig'
A component required a bean of type
'org.springframework.data.redis.connection.RedisConnectionFactory'
that could not be found.
```

### 9.2 修复

`pom.xml` 显式补回 `spring-boot-starter-data-redis`：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

意图清晰：之前是"通过 langchain4j-community-redis 间接拿到"，现在是"显式声明因为真的需要"。

### 9.3 流程教训（写进未来删依赖 checklist）

**删依赖时必须核传递依赖被谁用**——`mvn dependency:tree` 看 `<dependency>` 是不是别人的 transitive source。步骤：

1. `mvn dependency:tree | grep -B5 <artifactId>` 找出谁传递引入了它
2. 用 `mvn dependency:analyze` 找 "Used undeclared dependencies"（项目用了但没显式声明）和 "Unused declared dependencies"（声明了但没用）
3. 删之前在测试或本地启动一次完整流程验证
4. 删之后立即 `mvn -q clean compile` + 启动 smoke test

> 我本次只做了第 3、4 步的"compile + 子集测试"，但**没启动过应用**——`@SpringBootTest` 的子集不覆盖 `RedisCacheManagerConfig` 的 Bean 装配路径，导致回归逃逸到运行时。

### 9.4 是否改变 ADR-0011 的决策？

**不改变**。`RedisConnectionFactory` 不是 RedisChatMemoryStore 路径的专属——它服务于 `@Cacheable` / Spring Cache / Spring Session，本就该有。补回 `spring-boot-starter-data-redis` 是修复回归，不是推翻"删除 RedisChatMemoryStore"的决策。决策依然成立，只是删除时少做了一步传递依赖检查。

---

## 10. 事故 #4：`${user.dir}` 占位符不被 Spring 解析（2026-06-20 增补）

### 10.1 现象

应用启动成功，但用户调用 AI 代码生成功能时抛：

```
ERROR ... AiCodeGeneratorFacade: 保存失败: IOException: No such file or directory
```

并且前端 SSE 页面"卡死"——一直显示"生成中..."不结束。

### 10.2 根因

`application.yml` 写：

```yaml
prompt2app:
  storage:
    code-output-dir: ${PROMPT2APP_CODE_OUTPUT_DIR:${user.dir}/tmp/code_output}
```

意图：环境变量没设时 fallback 到 `user.dir/tmp/code_output`。**但**——

Spring 的 `${...}` 占位符只解析 `Environment` 中注册过的 property。`user.dir` 是 **JVM System property**（`System.getProperty("user.dir")`），Spring Boot **默认不把所有 System properties 暴露到 Environment**（除非用 `spring.main.allow-circular-references`-类的开关，或显式注册 `SystemEnvironmentPropertySource`）。

结果：`${user.dir}` 不被解析，字面量 `${user.dir}/tmp/code_output` 原样传给 `Prompt2AppProperties.storage.codeOutputDir`。`CodeFileSaverTemplate.buildUniqueDir` 调 `FileUtil.mkdir("${user.dir}/tmp/code_output/html_123")`——创建了一个**字面名为 `${user.dir}`** 的目录；后续 `FileUtil.writeString` 写到不存在的子目录下，抛 IOException。

### 10.3 第二层 bug：页面卡死

`AiCodeGeneratorFacade.processCodeStream` 的 `doOnComplete`：

```java
}).doOnComplete(() -> {
    try {
        ...
        codeFileSaverExecutor.executeSaver(...);  // 抛 IOException
    } catch (Exception e) {
        log.error("保存失败: {}", e.getMessage());  // 吞了！
    }
});
```

`catch (Exception)` **吞掉了异常**，没有向 SSE Flux 下游传播 `onError` 信号——前端订阅者永远等不到完成或失败，UI 卡在"生成中..."。

这是 **Phase 8 之前就存在的代码缺陷**（不是 ADR-0011 引入），事故 #4 把它顶了出来。本次按 Charter §3「克制」原则**不夹带修**，记入 backlog。

### 10.4 修复（第一次尝试 — b13146a，未生效）

**A. `application.yml`** —— 占位符 fallback 改为空字符串：

```yaml
prompt2app:
  storage:
    code-output-dir: ${PROMPT2APP_CODE_OUTPUT_DIR:}
    code-deploy-dir: ${PROMPT2APP_CODE_DEPLOY_DIR:}
    screenshots-dir: ${PROMPT2APP_SCREENSHOTS_DIR:}
```

留空时，`Prompt2AppProperties` 的 Java 字段默认值 `System.getProperty("user.dir") + "/tmp/code_output"` 生效——这是真正能拿到工作目录的途径。

**B. `Prompt2AppProperties`** —— 启动期占位符泄露校验（路径含 `${` 立刻 `IllegalStateException` 崩）。

### 10.5 第一次修复未生效的根因

用户重启应用再次调用 AI 生成，仍报 `IOException: No such file or directory`，且 `/Volumes/SSD/Dev/project/heavy/yu-pi/Prompt2App/tmp/code_output` 目录从未被创建。

**真因**：Spring Boot `@ConfigurationProperties` binding 把 **空字符串视为"已设值"**，直接调 setter 把字段设为 `""`，**Java 字段默认值根本没有机会生效**。所以：

- `storage.codeOutputDir = ""` （空字符串，不是 `null`）
- `CodeFileSaverTemplate.buildUniqueDir` 拼 `"" + "/" + "html_123"` = `/html_123`
- `FileUtil.mkdir("/html_123")` —— 这是要在文件系统根目录创建！macOS 沙箱权限不足
- `FileUtil.writeString` 写到 `/html_123/index.html` 抛 `No such file or directory`

第一次修复的 `validateNoPlaceholderLeak` 校验也没触发——因为这次根本没有 `${` 字面量了，只是空字符串。

### 10.6 修复（第二次尝试 — 升级版）

**核心思路**：在 setter 里做兜底，让 Spring Boot binding 调 setter 时就把空串 / null / 占位符字面量统一转成真实路径。

```java
@Data
public static class Storage {
    @Setter(AccessLevel.NONE)   // 关键：排除 Lombok 自动 setter
    private String codeOutputDir;
    @Setter(AccessLevel.NONE)
    private String codeDeployDir;
    @Setter(AccessLevel.NONE)
    private String screenshotsDir;
    private String codeDeployHost = "http://localhost";

    public void setCodeOutputDir(String codeOutputDir) {
        this.codeOutputDir = resolve(codeOutputDir, "tmp/code_output");
    }
    public void setCodeDeployDir(String codeDeployDir) {
        this.codeDeployDir = resolve(codeDeployDir, "tmp/code_deploy");
    }
    public void setScreenshotsDir(String screenshotsDir) {
        this.screenshotsDir = resolve(screenshotsDir, "tmp/screenshots");
    }

    private static String resolve(String configured, String defaultSub) {
        if (configured == null || configured.isBlank() || configured.contains("${")) {
            return System.getProperty("user.dir") + "/" + defaultSub;
        }
        return configured;
    }
}
```

`@Setter(AccessLevel.NONE)` 是关键——不加的话 `@Data` 会自动生成 setter 覆盖手写的，兜底逻辑不生效。

`resolve()` 统一处理三种坏值：
1. `null` —— Spring Boot 未配置该 key 时
2. 空字符串 `""` / 纯空白 —— yml 给了 `${VAR:}` 但 VAR 未设时
3. 含 `${` 字面量 —— 占位符未解析时（防御性，理论上 Spring 已解析过）

调用方零改动——`getCodeOutputDir()` 等 getter 名字不变，返回的就是兜底后的真实路径。

### 10.7 教训（写进未来配置 checklist）

1. **不要在 `application.yml` 用 `${user.dir}` / `${user.home}` 等 JVM System property 占位符**——它们不在 Spring Environment 中
2. **不要假设 Java 字段默认值会在 yml 给空串时生效**——Spring Boot binding 把空串当"已设值"，字段默认值被覆盖
3. **需要兜底逻辑时，写在 setter 里**，而不是字段默认值；用 `@Setter(AccessLevel.NONE)` 防止 Lombok 覆盖
4. **关键路径配置加启动期校验**——fail-fast 优于运行时暴雷（但校验逻辑要正确，第一次的 `validateNoPlaceholderLeak` 只检 `${`，漏了空串场景）
5. **修复后必须实际启动应用跑端到端流程**——子集单测不覆盖 `FileUtil.mkdir` 真实文件系统行为

### 10.8 是否改变 ADR-0011 决策？

**不改变**。事故 #5 是 ADR-0010 配置外部化时 yml 写法的副作用，与 ADR-0011 删 RedisChatMemoryStore 无关。但因为它出现在 ADR-0011 启动事故链的下游（用户启动应用 → 调用 AI 生成 → 报错），所以记在此处作为整条事故链的收尾。
