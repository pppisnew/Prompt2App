# Milestones

> 项目总路线图。22 天主线 / 8 Phase / 4 个面试爆点 + Phase 8 后续增量。每完成一个 Phase 必须更新本表。

---

## 总览

| Phase | 名称 | 工时 | 状态 | 关键产出 | 面试价值 |
| --- | --- | --- | --- | --- | --- |
| **0** | 评测基线 | 2d | ✅ Done | 25 case + 项目重命名（ADR-0009）+ Evaluator 端到端 + baseline（stub 模式） | ⭐⭐⭐⭐⭐ |
| **1** | 模块化单体收敛 | 2d | ✅ Done | ADR-0001 + 删除 microservice + 6 包结构 + module-design v1 + evaluator 回归 9/9 | ⭐⭐⭐ |
| **2** | 删 LangChain4j Patch | 2d | ✅ Done | ADR-0002 + 1.1.0→1.5.1 + 删 1450 行 patch + API 适配 | ⭐⭐⭐⭐ |
| **3** | Tool 安全体系 | 4d | ✅ Done | ADR-0004 + 三层防御（Layer 1/2/3）+ 27 单测进 CI + 5 tool 接入 | ⭐⭐⭐⭐⭐ |
| **4** | AI Router 重写 | 4d | ✅ Done | ADR-0003 + 两层路由 + 17 单测 + **80% 准确率 / 88% 命中率** | ⭐⭐⭐⭐⭐ |
| **5** | Eval 体系自动化 | 5d | ✅ Done | ADR-0005 + 三维评分 + DiffReporter + CI 门控 + 33 新单测 (86/87) | ⭐⭐⭐⭐⭐ |
| **6** | 质量指标埋点 | 3d | ✅ Done | generation_metric 表 + 4 SQL 聚合 + REST endpoint + 8 单测 | ⭐⭐⭐⭐ |
| **7** | 收尾 | 2d | ✅ Done | ADR-0006/0007 + README v2 + grafana/prometheus 清理 + Charter v1.0 锁定 | ⭐⭐⭐ |
| **8** | 配置统一化 | 1d | ✅ Done | ADR-0010 + spring-dotenv + Prompt2AppProperties + 7 调用点迁移 + .env.example + Charter v1.0→v1.1 | ⭐⭐⭐ |
| | **合计** | **23d** | | | |

---

## Phase 详情

### Phase 0 · 评测基线（2d）

**目标**：建立质量评估的"尺子"，作为后续所有重构的回归基准。

- 完成标准：见 [`current-phase.md`](./current-phase.md)
- 关键产出：`eval/cases/*.yaml` ×25，`eval/reports/baseline.md`
- ADR：ADR-0008（已 Accepted）

---

### Phase 1 · 模块化单体收敛（2d）

**目标**：删除微服务双份代码，主线包按领域分模块。

- 删除目标：`yu-ai-code-mother-microservice/`（用 git mv 到 legacy/ 或直接 rm，由 ADR-0001 决定）
- 包重组：`com.prompt2app` → `app / router / agent / eval / metric / infra`
- 关键产出：ADR-0001
- DoD：见 [`../governance/definition-of-done.md`](../governance/definition-of-done.md#phase-1--模块化单体收敛)

---

### Phase 2 · 删 LangChain4j Patch（2d）

**目标**：删除 `dev/langchain4j/` 源码覆盖包，升级到稳定版。

- 删除目标：`src/main/java/dev/langchain4j/`
- 关键产出：ADR-0002
- DoD：见 governance/definition-of-done.md

---

### Phase 3 · Tool 安全体系（4d）⭐ 面试爆点

**目标**：三层防御 + 20 单测进 CI。

- 三层：Schema 校验 / 工作目录绑定 / 调用次数熔断
- 关键产出：ADR-0004，`src/test/java/.../tool/safety/*Test.java`
- DoD：20+ 测试全过、CI 红线

---

### Phase 4 · AI Router 重写（4d）⭐ 面试爆点

**目标**：规则 + LLM 兜底两层路由 + 决策埋点。

- 关键产出：ADR-0003
- 评测：路由准确率与 case `expected_strategy` 对比

---

### Phase 5 · Eval 体系自动化（5d）⭐ 面试爆点

**目标**：评测执行器升级，CI 自动回归。

- 三维评分：编译 / 渲染 / LLM-Judge
- 关键产出：ADR-0005，`eval/reports/<sha>.md` 自动生成

---

### Phase 6 · 质量指标埋点（3d）

**目标**：12 维 `generation_metric` 表 + 一页 SQL 报表。

- 关键产出：表结构 + 报表页（不上 Grafana）
- 相关 ADR：可能涉及 ADR-0006

---

### Phase 7 · 收尾（2d）

- 写 ADR-0006（不上 MinIO） + ADR-0007（不引入 Workflow）
- README.md 改写为面向用户/招聘官版本
- 简历段落定稿

---

### Phase 8 · 配置统一化（1d）

**目标**：消除所有部署变量的硬编码，建立单一配置入口。

- 关键产出：ADR-0010 + `Prompt2AppProperties` + `.env.example` + 7 调用点迁移
- 触发：Charter v1.0 锁定后用户审计发现 5 处硬编码（AppConstant 路径/host、application.yml 明文密钥、CodeFileSaver 静态字段、@Value 重复定义、`System.getProperty("user.dir")` 拼接）
- 副产物：Charter v1.0 → v1.1（§4 增加"配置外部化"质量底线）
- 工具链问题修复：maven-compiler-plugin 3.14.0 + Lombok 注解处理 → `<proc>full</proc>`

---

## 总产出清单（项目结束时应有）

- ✅ 一个能跑的模块化单体（Spring Boot 3 + Vue 3）
- ✅ 25 case 评测集 + CI 回归
- ✅ 8 篇 ADR（编号 0001-0008）
- ✅ 1 张 metric 表 + 1 个 SQL 报表页
- ✅ 一段简历 + 4 个 5 分钟面试故事
- ✅ 完整的治理体系（Charter / Working Rules / DoD / ACP / Tasks）

---

## 状态变更记录

| 日期 | Phase | 旧状态 | 新状态 | 备注 |
| --- | --- | --- | --- | --- |
| 2026-06-18 | Phase 0 | Pending | In Progress | 项目重构启动，分支 feature/ai-engineering-rebuild 创建 |
| 2026-06-18 | Phase 0 | — | （越界） | ADR-0009 批准项目重命名为 Prompt2App，已完成 |
| 2026-06-18 | Phase 0 | — | （进度） | 25 case 全部就位（HTML 7 / MultiFile 8 / Vue 10），剩余执行器 + baseline |
| 2026-06-18 | Phase 0 | In Progress | **Done** | Evaluator 9/9 测试通过；baseline.md（stub 模式）生成；mvn compile 已验证（JDK 21 + Lombok） |
| 2026-06-18 | Phase 1 | Pending | In Progress | 微服务删除 + 按域 6 包重组（160 文件 git mv + sed），ADR-0001 Accepted |
| 2026-06-18 | Phase 1 | In Progress | **Done** | mvn compile 191 class + evaluator 9/9 持平 + module-design.md v1 |
| 2026-06-18 | Phase 2 | Pending | In Progress | ADR-0002 起草，pom.xml 升级到 langchain4j-bom 1.5.1，删 patch 1450 行 |
| 2026-06-18 | Phase 2 | In Progress | **Done** | API 适配 onPartialToolExecutionRequest → beforeToolExecution；mvn compile 180 class；evaluator 9/9 持平 |
| 2026-06-18 | Phase 3 | Pending | In Progress | ADR-0004 起草，PathValidator/Sandbox/ToolCallCounter 三层就绪，27/27 safety 单测过 |
| 2026-06-18 | Phase 3 | In Progress | **Done** | 5 个 file tool 全部接入 safety；mvn test 36/36（27 safety + 9 evaluator）；面试爆点 #1 落地 |
| 2026-06-18 | Phase 4 | Pending | In Progress | ADR-0003 起草，RoutingDecision/RuleRouter/RoutingService 三类就绪；接入两个调用点 |
| 2026-06-18 | Phase 4 | In Progress | **Done** | 规则调优后 Layer 1 准确率 60%→80%、命中率 88%；17 router 单测全过；面试爆点 #2 落地 |
| 2026-06-19 | Phase 5 | Pending | In Progress | ADR-0005 + Scorer 接口 + 4 个评分实现 + DiffReporter + CI workflow |
| 2026-06-19 | Phase 5 | In Progress | **Done** | 33 个新单测全过；86/87 总通过；CI 三大门控就绪；面试爆点 #3 落地 |
| 2026-06-19 | Phase 6 | Pending | **Done** | generation_metric 表 + 4 聚合 SQL + REST + 8 单测；94/95 总通过 |
| 2026-06-19 | Phase 7 | Pending | **Done** | ADR-0006/0007 + README v2 + grafana/prometheus 清理 + Charter v1.0 锁定 = 项目完成 |
| 2026-06-19 | Phase 8 | Pending | In Progress | Charter v1.0 锁定后增量；ADR-0010 起草，spring-dotenv 接入，Prompt2AppProperties 落地 |
| 2026-06-19 | Phase 8 | In Progress | **Done** | 7 调用点迁移 + 死代码 CodeFileSaver 删除 + maven-compiler proc=full 修复 + 95/94 基线保持 + Charter v1.1 |
| 2026-06-19 | Phase 8（增量 #1）| Pending | **Done** | 启动事故 #1+#2 最小修复：DB 自动建库 + Redis namespace 隔离（无 ADR） |
| 2026-06-19 | Phase 8（增量 #2）| Pending | **Done** | 启动事故 #3：ADR-0011 删除 RedisChatMemoryStore（langchain4j-community-redis 依赖回收）|
