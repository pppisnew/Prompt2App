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
