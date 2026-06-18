# ADR-0001：从微服务回退到模块化单体

- **状态**：Accepted
- **日期**：2026-06-18
- **决策者**：项目作者
- **相关 Phase**：Phase 1 · 模块化单体收敛
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

教学版 yu-ai-code-mother 在 commit `893918c`（tag `microservice-final`）已完成「微服务改造」，仓库同时存在两套代码：

```
yu-ai-code-mother/
├── src/                                  ← 单体版本
│   └── main/java/com/prompt2app/         ← 19 个顶层包
└── yu-ai-code-mother-microservice/       ← 微服务版本（独立 Maven 树）
    ├── pom.xml
    └── yu-ai-code-{ai,app,client,common,model,screenshot,user}/
```

两套代码的事实判断：

- **业务重复**：单体的 controller / service / mapper 在微服务版本里被切成了 7 个模块，但业务逻辑相同。
- **Dubbo + Nacos 的引入零收益**：3 个真实业务服务（user / app / screenshot）之间不存在独立扩缩容诉求；本地 RPC 调用增加了 30%+ 的调试成本；Nacos 配置中心、Dubbo 协议均无业务驱动。
- **作品集场景**：本项目无团队协作、无线上 SLA 压力。微服务的"组织治理价值"（多团队独立交付）在单人项目里完全不存在。
- **面试不利**：面试官追问"为什么拆？"时，"教程让我拆的"是减分回答；"我评估了 RPC 必要性后选择不拆"是加分回答。

主项目 19 个顶层包按"层"组织（controller / service / mapper / model 等），按"领域"分包能更清楚地展示模块边界，也更贴合 Phase 4-6 的 Router / Agent / Metric 工作。

---

## 备选方案（Options）

### 方案 A：保留微服务版本作为"对照分支"

- **做法**：删除单体 src/，保留 microservice/
- **优点**：保留微服务架构展示
- **缺点**：
  - 多模块 Maven 项目调试更复杂
  - 单人维护下 Dubbo 跨进程调用每次都要起 Nacos
  - 后续 evaluator / Router / Tool Safety 等 Phase 工作都要在 7 个模块间跨服务实现，工作量翻倍
- **长期成本**：高

### 方案 B：保留单体，删除微服务（**本决策**）

- **做法**：删除 `yu-ai-code-mother-microservice/`，主项目按领域分包
- **优点**：
  - 一份代码、单进程启动、IDE 跳转友好
  - 后续 Phase 工作都在单体内推进，复杂度可控
  - 简历仍可以讲"评估后回退到模块化单体"——这本身就是架构能力
- **缺点**：
  - 失去"我会 Dubbo + Nacos"的展示——但这本身就不是核心卖点
  - microservice/ 删除后，git tag `microservice-final` 是唯一存档
- **长期成本**：低

### 方案 C：模块化单体 + Maven 多模块拆分

- **做法**：保留单体进程，但 src/ 拆成 `app-module / agent-module / infra-module` 等独立 Maven artifact
- **优点**：模块边界用 Maven 强制（无法跨模块互相 import）
- **缺点**：
  - 5+ Maven 模块对单人项目过度
  - 每加一个新文件要先想"放哪个模块"，开发摩擦大
  - 与 Phase 5 的 ArchUnit 守护功能重复
- **长期成本**：中

### 方案 D：保留双版本

- **做法**：不删任何东西
- **优点**：最大保留性
- **缺点**：每次重构都要在两套上做；几周后两套必然发散
- **长期成本**：极高

---

## 决策（Decision）

**选择方案 B**：删除微服务版本，单体按领域分 6 个顶层包（`app / router / agent / eval / metric / infra`）。

### 微服务删除策略

- **直接 `git rm -rf yu-ai-code-mother-microservice/`**
- **不归档**到 legacy/——git tag `microservice-final` 是充分的存档（可随时 `git checkout microservice-final` 复现）
- **同时删除** `microservice` 的 README / pom.xml 等所有附属文件

### 包映射表（完整）

| 旧包路径 | 新包路径 | 说明 |
| --- | --- | --- |
| `ai.AiCodeGenTypeRoutingService(Factory)?` | `router.AiCodeGenTypeRoutingService(Factory)?` | AI 路由策略服务，独立成 router 包 |
| `ai.AiCodeGeneratorService(Factory)?` | `agent.AiCodeGeneratorService(Factory)?` | Agent 主服务 |
| `ai.guardrail.*` | `agent.guardrail.*` | 输入/输出护栏 |
| `ai.model.*` | `agent.model.*` | AI 响应消息模型 |
| `ai.tools.*` | `agent.tools.*` | Tool Calling 工具集 |
| `core.*`（顶层 + builder/handler/parser/saver） | `agent.codegen.*` | 代码生成 pipeline |
| `langgraph4j.*` | `agent.workflow.*` | LangGraph4j 实验工作流（Phase 7 ADR-0007 决定是否保留） |
| `controller.*` | `app.controller.*` | 业务控制器 |
| `service.*`（含 impl） | `app.service.*` | 业务服务 |
| `mapper.*` | `app.mapper.*` | MyBatis-Flex Mapper |
| `model.{dto,entity,enums,vo}.*` | `app.model.{dto,entity,enums,vo}.*` | 业务领域模型 |
| `monitor.*` | `metric.*` | 监控埋点 |
| `annotation.*` | `infra.annotation.*` | 自定义注解（如 @AuthCheck） |
| `aop.*` | `infra.aop.*` | AOP 拦截器 |
| `common.*` | `infra.common.*` | BaseResponse / DeleteRequest / PageRequest 等 |
| `config.*` | `infra.config.*` | Spring 配置类 |
| `constant.*` | `infra.constant.*` | 常量类 |
| `exception.*` | `infra.exception.*` | 全局异常处理 |
| `generator.*` | `infra.generator.*` | MyBatis-Flex 代码生成器（dev 工具） |
| `manager.*` | `infra.manager.*` | CosManager 等基础设施管理器 |
| `utils.*` | `infra.utils.*` | 工具类 |
| `ratelimter.*` | `infra.ratelimiter.*` | **同时修拼写**（ter → iter） |
| `eval.*` | `eval.*` | 不动（Phase 0 已正确就位） |
| `Prompt2AppApplication` | 不动（位于根包 `com.prompt2app`） | 主启动类 |

### 模块边界约束（依赖方向）

```
       infra
        ▲
        │
   ┌────┼────┬────┬─────┐
   │    │    │    │     │
  app  router agent metric eval
   │    │    │           │
   └────┴────┴───────────┘
                ↑
                外部 LLM / DB / Redis / FS
```

- `infra/` 不依赖任何业务包
- `app/` 可依赖 `infra`、`agent`、`router`、`metric`
- `agent/` 可依赖 `infra`，**不**依赖 `app`
- `router/` 可依赖 `infra`、`agent`，**不**依赖 `app`
- `metric/` 可依赖 `infra`，**不**依赖业务包
- `eval/` 不依赖任何业务包（保持作为"尺子"的独立性）

> **未启用 ArchUnit 守护**：Phase 5 评测体系自动化时一并加。在那之前靠 code review + commit message 自律。

### 资源文件同步

- 3 个 MyBatis Mapper.xml 的 `namespace` 从 `com.prompt2app.mapper.*` 改到 `com.prompt2app.app.mapper.*`
- `Prompt2AppApplication.@MapperScan` 从 `"com.prompt2app.mapper"` 改到 `"com.prompt2app.app.mapper"`
- `application.yml` 中 Knife4j 的 `packages-to-scan: com.prompt2app.controller` 改到 `com.prompt2app.app.controller`

### 不做的事

- **不引入 Maven 多模块**（方案 C 已驳回）
- **不引入 ArchUnit**（Phase 5 再加）
- **不删 `dev/langchain4j/` patch**（Phase 2 处理）
- **不删 `agent/workflow/`（原 langgraph4j）**（Phase 7 ADR-0007 处理）
- **不重命名前端目录** `yu-ai-code-mother-frontend/`（前端独立演进，不强求与后端同步）

---

## 代价（Consequences）

### 正面

- 主项目从 19 个顶层包收敛到 6 个，新人/未来的自己更易理解模块边界
- 后续 Phase 4 / 6 / 5 的工作（Router / Metric / Eval 自动化）有干净的命名空间
- microservice 双份代码消除，git history 不再有"改了 A 没改 B"的风险
- 简历可以讲：「评估了 RPC 必要性后回退模块化单体」——架构判断力的体现

### 负面

- **140+ 文件改 import**：靠 sed + git mv 完成，PR review 难度高，但因为是机械替换，无业务逻辑改动
- **microservice/ 整体删除**：约 50 个 Java 文件 + 7 个 pom.xml，从工作树消失。**唯一存档** = git tag `microservice-final`
- **`@MapperScan` / mapper.xml namespace / yml 配置**等"包路径硬编码"位置需要逐一同步——遗漏会运行时报错（启动期就能发现）

### 中性

- `agent/workflow/`（原 langgraph4j）此 Phase 不重新设计，仅迁移路径——它的命运在 Phase 7 ADR-0007 决定
- ratelimter 拼写错误一并修复（影响约 8 文件），但没有把它升级为独立 ADR——拼写修复是机械操作

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估本决策：

- 项目从单人变成 ≥ 3 人协作 → 考虑回到 Maven 多模块或微服务
- 某个模块（如 agent/）的代码量超过 10K 行 → 考虑独立 artifact
- 出现"多团队同时改 app/" 这类组织级冲突 → 重新评估边界
- ArchUnit 引入后发现规则反复被违反 → 说明边界划分错误，重新分包

---

## 参考资料（References）

- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §3 「明确不做：微服务化」
- [`docs/governance/ai-working-rules.md`](../governance/ai-working-rules.md)
- [`docs/architecture/module-design.md`](../architecture/module-design.md) ← 本 ADR 落地后填充
- [`docs/reverse-engineering/项目逆向工程与Vibe-Coding复现指南.md`](../reverse-engineering/项目逆向工程与Vibe-Coding复现指南.md) §4 代码结构分析
- 业界参考：[Spring Modulith 文档](https://docs.spring.io/spring-modulith/reference/)（采用其"按域分包"思路，但不引入框架本身）
