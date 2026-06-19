# Task Record · 2026-06-19 · ADR-0011 删除 RedisChatMemoryStore

> **性质**：Phase 8 启动事故链上的第三个事件，由 ADR 主导决策。**不开新 Phase / 不修订 Charter**。

## 0. 元信息

- 触发：vanilla Redis 不识别 `JSON.GET`，`langchain4j-community-redis` 的 `RedisChatMemoryStore` 失败
- 决策载体：`docs/adr/0011-drop-redis-chat-memory-store.md`
- 处置选项：用户在选项 A/B/C 之间选 **C+ADR**（删除）；又额外问"如何让 Redis 层有效 / 是否需要"，经讨论确认 Redis 层在当前 Charter 假设下三种价值场景（多实例 / 高 heap 压力 / 冷启动加速）皆不成立，故采纳 C
- 决策的隐含前提（写在 ADR 第 3 节）：单实例部署 / heap 容量充足 / MySQL 回放 ≤10ms

## 1. 实际改动

### 删除

| 路径 | 原因 |
| --- | --- |
| `src/main/java/com/prompt2app/infra/config/RedisChatMemoryStoreConfig.java` | `RedisChatMemoryStore` 不再使用 |

### 修改

| 路径 | 改动 |
| --- | --- |
| `src/main/java/com/prompt2app/agent/AiCodeGeneratorServiceFactory.java` | 删 `import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;`<br>删 `@Resource RedisChatMemoryStore redisChatMemoryStore` 字段<br>`MessageWindowChatMemory.builder()` 链上去掉 `.chatMemoryStore(redisChatMemoryStore)` 调用<br>注释加一行解释（in-process / source of truth 是 chat_history 表 / 详见 ADR-0011）|
| `src/main/java/com/prompt2app/Prompt2AppApplication.java` | 删 `import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;`<br>`@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})` → `@SpringBootApplication`（exclude 已无意义，因为依赖被回收）|
| `pom.xml` | 删 `<dependency>langchain4j-community-redis-spring-boot-starter</dependency>`<br>删 `<langchain4j-community.version>1.5.0-beta11</langchain4j-community.version>` property（不再被引用）|

### 新增

| 路径 | 内容 |
| --- | --- |
| `docs/adr/0011-drop-redis-chat-memory-store.md` | 完整 ADR：4 备选方案 / 决策 / 代价 / 复盘指标 / 触发回头看条件 |
| `docs/tasks/2026-06-19-phase8-adr0011-drop-redis-chat-memory.md` | 本文件 |

### 索引更新

- `docs/adr/README.md`：加 ADR-0011 行
- `docs/roadmap/milestones.md`：加 Phase 8 增量 #1、#2 状态记录
- `docs/tasks/README.md`：加 14 篇任务索引

## 2. 关键的"未做"决策（边界明确）

- ❌ **未切 redis-stack** — 那是方案 A，与"删除冗余依赖"目的相反
- ❌ **未实现 Redis-as-cache + MySQL-as-fallback** — 那是方案 B，1.5-2d 工作量、增加运维依赖、收益接近 0
- ❌ **未自写 vanilla Redis ChatMemoryStore** — 那是方案 D，4-6h 过度工程
- ❌ **未删 spring-session-data-redis / Redisson / spring-data-redis** — 它们服务于 HTTP Session、分布式锁、`@Cacheable`，与 chat memory 无关
- ❌ **未修改 Charter** — 本 ADR 不修订 Charter，仅是 §3「克制添加」的一次具体应用

## 3. 验证

- ✅ `mvn -q clean compile`：BUILD SUCCESS（170 sources，比之前少 1 个）
- ✅ `mvn test -Dtest='com.prompt2app.eval.*Test,com.prompt2app.router.*Test,com.prompt2app.agent.tools.safety.*Test,com.prompt2app.metric.*Test'`：
  - **Tests run: 95, Failures: 0, Errors: 1**
  - 1 个 error 仍是 pre-existing 的 `AiCodeGenTypeRoutingServiceTest`（要 DB），与 Phase 1~7 各阶段一致
  - 相对 Phase 8 主提交（`c3c030e`）和 Phase 8 增量 #1（`11d4818`）完全持平
- ⏳ 实际启动行为：用户清 redis 后 `./start.sh dev`，AI 代码生成不再抛 `JedisDataException: ERR unknown command 'JSON.GET'`

## 4. 后续触发回头看的条件（写在 ADR-0011 §6，备查）

| 条件 | 对应未来 ADR |
| --- | --- |
| 引入水平扩展 / 多实例 | ADR-0012-A「跨实例 chat memory 共享」 |
| chat 高并发使 heap 出现压力 | ADR-0012-B「卸载 chatMemory 到外部 store」 |
| MySQL `chat_history` 回放变慢（>50ms） | ADR-0012-C「chat memory 缓存层」 |

> 这就是为什么"删除"不等于"永远不做"。
