# Task Record · 2026-06-19 · Phase 8 增量 · 启动事故复盘修复

> **性质**：Phase 8（配置统一化）的延伸最小修复，**不开新 Phase**、**不立 ADR**——按用户事故报告处置选项 A 落实。

## 0. 背景

Phase 8 commit `c3c030e`（feat: 配置统一化）合入后首次启动暴露两类故障：

1. **`Unknown database 'prompt2app'`** —— DB 重命名为 prompt2app 后 MySQL 中没有该库，HikariCP 启动失败 → 容器无法初始化
2. **`ClassNotFoundException: com.peng.zerocodeappsandbox.model.entity.User`** —— Spring Session 默认 JDK 序列化器尝试反序列化 Redis 中**另一个项目**残留的 session 数据，每个 HTTP 请求都崩

## 1. 决策与争议

用户事故报告把 "Redis serializer 切 Jackson2JsonRedisSerializer" 列为高优。**我提出反对**：

| 用户判断 | 我的判断 | 理由 |
| --- | --- | --- |
| 根因 = JDK 序列化机制 | 根因 = **Redis keyspace 没隔离**（多项目共用同一 Redis DB 0） | 即使切 JSON serializer，旧数据仍在 keyspace 中可被读到，只是反序列化错型号而非 ClassNotFound——**namespace 隔离才是直接修复** |
| serializer 切换 = 高优、立即做 | serializer 切换 = **值得一篇独立 ADR**，本次不做 | Spring Session 切 JSON 有非平凡代价（无参构造、Jackson 类型信息、SaveMode 行为变化、+30~50% 体积），不是无损升级；当前 namespace 隔离已足够 |
| Flyway / `spring.sql.init` 自动建表 = 中优 | 暂不做、单独 ADR | `spring.sql.init` 不解决 `Unknown database`（不建库只建表）；Flyway 是完整的 Phase，不该和事故修复绑一起 |

最终落地：**两行配置修复 + 零依赖 + 零代码改动**。

## 2. 改动

### `src/main/resources/application.yml`

```yaml
spring:
  session:
    store-type: redis
    timeout: ${SESSION_TIMEOUT:2592000}
    redis:
      # 防跨项目 Redis 污染
      namespace: ${SPRING_SESSION_REDIS_NAMESPACE:prompt2app:session}
  datasource:
    # createDatabaseIfNotExist=true 让首启动自动建库；表结构仍由 sql/create_table.sql 手动执行
    url: ${DB_URL:jdbc:mysql://localhost:3306/prompt2app?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4}
  cache:
    redis:
      key-prefix: ${SPRING_CACHE_REDIS_KEY_PREFIX:prompt2app:cache:}
      use-key-prefix: true
```

### `.env.example`

- `DB_URL` 例子更新为含 `createDatabaseIfNotExist=true&useSSL=false&serverTimezone=...&characterEncoding=utf8mb4` 的完整连接串
- 新增分组 "Spring Session / Cache 命名空间（防跨项目 Redis 污染）"
  - `SPRING_SESSION_REDIS_NAMESPACE=prompt2app:session`
  - `SPRING_CACHE_REDIS_KEY_PREFIX=prompt2app:cache:`

## 3. 校验

- ✅ Spring Boot 3.5.4 config metadata 验证：`spring.session.redis.namespace`、`spring.cache.redis.key-prefix`、`spring.cache.redis.use-key-prefix` 都是真实存在的绑定属性
- ✅ `mvn compile` 通过
- ⏳ 实际启动行为待用户在干净 MySQL + Redis 环境复测（清 redis + 删除 prompt2app 库后跑 `./start.sh dev`）

## 4. 范围限制（明确 NOT 做）

- ❌ 未切 Redis serializer（留作 ADR-0011 候选；下次再被坑或 1 周内反复出现序列化异常时再立项）
- ❌ 未引 Flyway / 未启用 `spring.sql.init`（库已能自动建，表结构 SQL 手动跑足够；引 Flyway 需要单独 Phase）
- ❌ 未做反序列化失败降级（用户已标低优）
- ❌ 未改 PROJECT_CHARTER（没有触发 §6 修订条件）
- ❌ 未改 ADR-0010（namespace 是 ADR-0010 配置外部化的自然延伸，不是新决策）

## 5. 后续观察项

- **触发 ADR-0011 的条件**：1 周内再次出现 `ClassNotFoundException` / `SerializationException` / 跨项目 session 污染
- **触发 Flyway Phase 的条件**：表结构开始频繁变更，或团队 > 1 人需要协作迁移
