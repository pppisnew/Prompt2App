# Module Design

> **状态**：v1（Phase 1 落地）
> **对应 Phase**：Phase 1 · 模块化单体收敛
> **相关 ADR**：[ADR-0001](../adr/0001-modular-monolith.md)

---

## 包结构总览

```
com.prompt2app/
├── Prompt2AppApplication.java        ← Spring Boot 主启动类
│
├── app/                              ← 业务领域（应用 / 用户 / 对话历史 / CRUD）
│   ├── controller/                     6 个 REST + SSE controller
│   ├── service/                        AppService / UserService / ChatHistoryService 等
│   │   └── impl/
│   ├── mapper/                         MyBatis-Flex mapper
│   └── model/
│       ├── dto/{app,chathistory,user}/ 请求 DTO
│       ├── entity/                     App / User / ChatHistory
│       ├── enums/                      CodeGenTypeEnum 等
│       └── vo/                         返回 VO
│
├── router/                           ← AI Router（Phase 4 重写的目标）
│   ├── AiCodeGenTypeRoutingService.java
│   └── AiCodeGenTypeRoutingServiceFactory.java
│
├── agent/                            ← AI Agent + 代码生成 + 工作流
│   ├── AiCodeGeneratorService.java     主 Tool Calling Agent
│   ├── AiCodeGeneratorServiceFactory.java
│   ├── guardrail/                      输入/输出护栏
│   ├── model/                          AI 响应消息模型
│   ├── tools/                          Tool Calling 工具集（File* / ExitTool 等）
│   ├── codegen/                        代码生成 pipeline
│   │   ├── AiCodeGeneratorFacade.java
│   │   ├── CodeFileSaver.java / CodeParser.java
│   │   ├── builder/ handler/ parser/ saver/  各策略实现
│   └── workflow/                     ← 原 langgraph4j（Phase 7 ADR-0007 决定保留 vs 删除）
│       ├── ai/ config/ demo/ model/ node/ state/ tools/
│
├── eval/                             ← 评测体系（Phase 0 / Phase 5）
│   ├── EvalCase / EvalCaseLoader / AgentInvoker / RubricScorer
│   ├── MarkdownReporter / EvalRunner
│
├── metric/                           ← 监控埋点（原 monitor）
│   └── AiModelMetricsCollector / AiModelMonitorListener / MonitorContext(Holder)
│
└── infra/                            ← 横向基础设施（不依赖任何业务包）
    ├── annotation/                     @AuthCheck
    ├── aop/                            AuthInterceptor
    ├── common/                         BaseResponse / DeleteRequest / PageRequest / ResultUtils
    ├── config/                         8 个 Spring 配置类
    ├── constant/                       AppConstant / UserConstant
    ├── exception/                      BusinessException / ErrorCode / GlobalExceptionHandler
    ├── generator/                      MyBatis-Flex code generator (dev tool)
    ├── manager/                        CosManager
    ├── ratelimiter/                  ← 修拼写（原 ratelimter）
    │   ├── annotation/ aspect/ config/ enums/
    └── utils/                          CacheKeyUtils / SpringContextUtil / WebScreenshotUtils
```

## 模块边界

```
              infra
                ▲
                │
       ┌────────┴────────┬─────────┬──────────┐
       │                 │         │          │
      app  ◀─── ───▶   metric    router  ──▶  agent
                                              │
                                              ▼
                                        外部 LLM / FS / DB

       ┌──────────────────────────────────────┐
       │    eval （独立尺子，不依赖业务包）    │
       └──────────────────────────────────────┘
```

| 模块 | 允许依赖 | 禁止依赖 |
| --- | --- | --- |
| `infra` | 仅三方库 | 任何业务包 |
| `app` | `infra`, `agent`, `router`, `metric` | `eval` |
| `router` | `infra`, `agent` | `app`, `metric`, `eval` |
| `agent` | `infra` | `app`, `router`, `metric`, `eval` |
| `metric` | `infra` | `app`, `router`, `agent`, `eval` |
| `eval` | 仅 SnakeYAML / JUnit | 全部业务包（保持作为"尺子"的独立性） |

> **当前依靠 code review 自律**。Phase 5 评测体系自动化时一并加 ArchUnit 守护规则。

## 旧→新 包映射表

| 旧 | 新 | 备注 |
| --- | --- | --- |
| `ai.AiCodeGenTypeRoutingService(Factory)?` | `router.AiCodeGenTypeRoutingService(Factory)?` | 路由层独立 |
| `ai.AiCodeGeneratorService(Factory)?` | `agent.AiCodeGeneratorService(Factory)?` | Agent 主服务 |
| `ai.{guardrail,model,tools}` | `agent.{guardrail,model,tools}` | Agent 子模块 |
| `core.*` | `agent.codegen.*` | 代码生成 pipeline |
| `langgraph4j.*` | `agent.workflow.*` | 工作流实验（命运待 ADR-0007） |
| `controller / service / mapper / model` | `app.{controller,service,mapper,model}` | 业务领域聚合 |
| `monitor.*` | `metric.*` | 命名更准确 |
| `annotation / aop / common / config / constant / exception / generator / manager / utils` | `infra.<同名>` | 横向基础设施集中 |
| `ratelimter.*` | `infra.ratelimiter.*` | **修拼写** + 归位 |
| `eval.*` | `eval.*` | 不动（Phase 0 已正确就位） |

## 配套配置同步

- `Prompt2AppApplication.@MapperScan`：`com.prompt2app.mapper` → `com.prompt2app.app.mapper`
- 3 个 mapper.xml `<mapper namespace="...">`：同步到 `com.prompt2app.app.mapper.*`
- `application.yml` Knife4j `packages-to-scan`：`com.prompt2app.controller` → `com.prompt2app.app.controller`

## 演进锚点

- 单 `app` 模块代码量 > 10K 行 → 考虑按"领域"再切（user / app / chathistory 各自独立）
- 团队规模 > 1 人 → 考虑回到 Maven 多模块或微服务（参见 ADR-0001 复盘条件）
- ArchUnit 引入后规则被反复违反 → 说明边界划分错误，需要重新评估

## 参考

- [ADR-0001](../adr/0001-modular-monolith.md) 决策论证 + 完整代价分析
- [ADR-0009](../adr/0009-project-rename-to-prompt2app.md) 上一次大规模机械重命名的先例（178 文件）
- [PROJECT_CHARTER.md](../../PROJECT_CHARTER.md) §3 「明确不做：微服务化」
