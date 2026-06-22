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

## 事故修复链收尾（2026-06-20）

Phase 8 合入后用户端到端测试暴露事故链 #1–#10，已全部修复：

| # | 事故 | ADR / Task Record |
| --- | --- | --- |
| 1–2 | DB 自动建库 + Redis namespace 隔离 | [task](../tasks/2026-06-19-phase8-bootstrap-incident-fix.md) |
| 3 | RedisChatMemoryStore 删除 | [ADR-0011](../adr/0011-drop-redis-chat-memory-store.md) |
| 4–5 | 依赖回归 + `${user.dir}` 占位符泄露 | ADR-0011 §9–§10 |
| 6–8 | 静态资源双目录 + 前端 URL 统一 | [ADR-0012](../adr/0012-static-resource-dual-dir-and-url-unification.md) |
| 9 | generation_metric 列名 snake_case | [task](../tasks/2026-06-20-phase8-incident-chain-6-to-10.md) |
| 10 | COS 诊断（误诊，COS 本身正常） | [task](../tasks/2026-06-20-phase8-incident-chain-6-to-10.md) |

**治理补救**：事故 #6–#8 的代码修复先于 ADR 完成（违反 Charter §4 §5），ADR-0012 + task record 为事后补写。ADR-0012 §7 记录流程教训。

---

## Eval 增量（2026-06-21 / 06-22）

Phase 8 锁定后，Eval 维度做了两轮增量修复：

| 日期 | Task | 结果 |
| --- | --- | --- |
| 2026-06-21 | [MULTI_FILE + VUE_PROJECT 0 分修复](../tasks/2026-06-21-multi-file-vue-eval-fix.md) | 总均分 19.75 → 55.06；MULTI_FILE 三层根因（prompt/parser/saver）修复 |
| 2026-06-21/22 | [VUE Render 评分修复（build 可观测 + RenderScorer 真检 dist）](../tasks/2026-06-21-vue-render-build-observability.md) | **VUE_PROJECT Render 0→100（10/10）**，VUE 维度均分 0→18.46；总均分 55.06→35.38 但**经逐 case 实测确证回归源是 LLM 抽样随机性，非代码引入** |
| 2026-06-22 | [评测多轮均分 + temperature=0 + 断点续跑](../tasks/2026-06-22-eval-multi-round-determinism.md)（[ADR-0013](../adr/0013-eval-multi-round-determinism.md)）| ✅ 完成。**3 轮均分 40.83 ± 7.52 / 100**（HTML 80.18±29.65 / MULTI 57.44±21.77 / VUE 0.00±0.00）。揭示 VUE 维度 `readMergedOutput` bug 是当前最大杠杆项（10/10 case 全 0 分），方案 D 已定但待下个独立 task 实施 |
| 2026-06-22 | [VUE mergedOutput 改读源码 + 文件清单（方案 D）](../tasks/2026-06-22-vue-mergedoutput-source-fix.md) | ✅ 完成。VUE Rubric 通过率 0/10 → **6/10**（离线验证，`package.json` 命中率 0→10/10）。剩余 4 个 miss 是 rubric 设计问题（`addEventListener`/`摄氏`/`解析`/`X 胜利`），独立 backlog |

---

## 下一 Phase 预告

无固定计划。Phase 8 是按需启动的"v1.0 后增量"。后续 Phase 9+ 按需 + 走 ADR + 走 Charter §6 修订流程启动。

候选议题（非承诺，仅记录）：
- **根因 A：Vue prompt 模板要求 import 路径与已声明文件清单一致**（独立 task，杜绝 LLM 生成不存在引用导致 build 失败）
- ~~**LLM 评测随机性治理**~~ → ✅ 已落地于 ADR-0013 / [2026-06-22 task](../tasks/2026-06-22-eval-multi-round-determinism.md)
- **LLM-Judge 第三维度启用**：`LlmJudgeScorer`/`LlmJudgeService` 已就位，`RealEvalRunner` 当前仅挂 Rubric+Render 两维（注释写"先用两维"），把第三维接入即可
- **EvalRunner.runFull 报告路径 / prevReport 路径不一致**：本轮 reportFile=baseline-real.md，prevReport 却指向 baseline.md=stub 报告，导致 Diff 输出 `No previous baseline found`（注：ADR-0013 切到 MultiRound 后此路径已废弃，旧 EvalRunner 仍存）
- **AiModelMonitorListener 在评测无 HTTP 上下文环境抛 NPE**：被 langchain4j catch 不阻断评分，但日志噪音；评估是否在 listener 入口加 null 守卫
- 配置项校验（`@Validated` + JSR-380 约束）
- 配置审计 endpoint（`/actuator/configprops`）
- 多环境密钥的 Vault / Sealed Secrets 集成（仅当对外服务化时考虑）
- ~~CI workflow 接入~~ → ✅ **已澄清**（Phase 5 / ADR-0005 §7 已建好 `.github/workflows/eval.yml`；2026-06-22 只读探索证实测试 pattern 自然覆盖新增 56 测试）。小遗留：README CI badge URL 默认指 master，feature 分支状态不显示——commit + push 后修
- `AiCodeGeneratorFacade.processCodeStream` 的 `catch(Exception)` 吞异常不向 SSE 透传 `onError`（backlog，记于 ADR-0011 §10.3）
