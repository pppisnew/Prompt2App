# Current Phase

> **AI 注意**：每次开始任务前必读本文件。当前 Phase 之外的任何工作都需要走 ACP 流程。

---

## 🎯 当前 Phase

**Phase 8 · 配置统一化** ✅ **Done**（v1.0 → v1.1 微补丁）

- **状态**：✅ Done
- **开始日期**：2026-06-19
- **完成日期**：2026-06-19
- **责任人**：项目作者
- **上一 Phase**：Phase 7 ✅ Done（项目 v1.0 锁定）
- **触发 Charter 修订**：是（v1.0 → v1.1，§4 增加"配置外部化"质量底线）

---

## 完成情况（Definition of Done）

- [x] ADR-0010 写完并 Accepted
- [x] `pom.xml` 添加 `me.paulschwarz:spring-dotenv:4.0.0` 依赖
- [x] `application.yml` 重构为 `${VAR:default}` 模式（敏感值无明文）
- [x] 创建 `application-{dev,test,prod}.yml` profile 覆盖
- [x] 创建 `.env.example` 含全部可配置项 + 用途注释
- [x] `.gitignore` 包含 `.env` / `application-{dev,prod}.yml`
- [x] 创建 `Prompt2AppProperties` `@ConfigurationProperties` 类
- [x] `AppConstant` 路径常量迁出（保留业务规则常量 `GOOD_APP_PRIORITY` / `DEFAULT_APP_PRIORITY`）
- [x] 5 个 `user.dir + "/tmp/..."` 调用点迁到 `Prompt2AppProperties`
- [x] 消除重复定义（`CODE_DEPLOY_HOST` 仅 yml 一处 + props 一处映射）
- [x] mvn compile + mvn test 不退化（保持 **95/94**：1 pre-existing 失败 = baseline）
- [x] `architecture/system-overview.md` 含配置架构说明（来源优先级 + 关键组件 + 业务约束 + 已外化清单）
- [x] 额外修复：maven-compiler-plugin 3.14.0 + Lombok 注解处理（加 `<proc>full</proc>`）
- [x] Charter v1.0 → v1.1 微补丁

---

## 实际产出

详见 [`docs/tasks/2026-06-19-phase8-config-unification.md`](../tasks/2026-06-19-phase8-config-unification.md) 与 [`docs/adr/0010-config-unification.md`](../adr/0010-config-unification.md)。

关键交付：
- 1 篇新 ADR（0010）
- 1 个新配置类（`Prompt2AppProperties` Storage + Tool 嵌套）
- 4 个新 yml（base + dev/test/prod profile）
- 1 个 `.env.example`（~50 配置项分组 + 注释）
- 7 个调用点 + 1 个测试改造
- 1 个死代码删除（`CodeFileSaver.java`）
- 1 个工具链回归修复（maven-compiler `<proc>full</proc>`）
- Charter v1.0 → v1.1

---

## 下一 Phase 预告

无固定计划。Phase 8 是按需启动的"v1.0 后增量"。后续 Phase 9+ 按需 + 走 ADR + 走 Charter §6 修订流程启动。

候选议题（非承诺，仅记录）：
- 配置项校验（`@Validated` + JSR-380 约束）
- 配置审计 endpoint（`/actuator/configprops`）
- 多环境密钥的 Vault / Sealed Secrets 集成（仅当对外服务化时考虑）
