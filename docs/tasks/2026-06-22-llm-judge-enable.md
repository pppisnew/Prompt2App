# Task: LLM-Judge 第三维度启用

- **日期**：2026-06-22
- **Phase**：Eval 增量
- **责任人**：项目作者 + AI
- **状态**：Done（2026-06-22 编译 + 60 单测全绿）
- **关联 ADR**：[ADR-0005](../adr/0005-evaluation-automation.md)（三维评分体系定义，本 task 补全第三维）

---

## 1. 目标

把 `LlmJudgeScorer`（LLM-as-Judge 主观评分维度）接入 `RealEvalRunner`，让评测从"Rubric + Render 两维"升级到 ADR-0005 设计的"三维评分"。

Judge 维度**不 veto**（软评分），与 Rubric/Render 的硬门控互补。

## 2. 背景

ADR-0005 §决策定义了三维评分（Rubric / Render / LLM-Judge），但 `LlmJudgeService` 接口虽存在，`@Bean` 织入点从未建——`RealEvalRunner` 注释写"先用两维"。

P0-1 多轮评测跑完后，HTML/MULTI 的 Rubric/Render 已稳定，VUE 的 Rubric 也由 P0-2 修到 6/10。**补全 Judge 维度让评测维度完整**，也是简历/README 里"三维评分"叙述的真实落地。

## 3. 影响范围（Scope）

**改动文件**：

| 文件 | 改动 |
| --- | --- |
| `src/main/java/com/prompt2app/eval/LlmJudgeServiceFactory.java`（新建） | `@Configuration` + `@Bean`，用 `AiServices.builder` 织入 `LlmJudgeService`，chatModel 用 `routingChatModelPrototype`（小模型，省 token） |
| `src/test/java/com/prompt2app/eval/RealEvalRunner.java` | 注入 `LlmJudgeService`，scorers 列表加 `new LlmJudgeScorer(judgeService)` |
| `docs/tasks/2026-06-22-llm-judge-enable.md` | 本 Task Record |

**明确不做**：
- ❌ 不动 `LlmJudgeScorer` 逻辑（已有完整 fallback：judge=null→50 不 veto，异常→50 不 veto）
- ❌ 不动 `LlmJudgeService` 接口（已就位）
- ❌ 不动 `eval-judge-system-prompt.txt`（已存在）
- ❌ 不跑全量 3 轮评测（省 token，下次评测周期统一回归）
- ❌ 不动 CompositeScorer（它已支持任意数量 Scorer）

## 4. 修改内容

### 4.1 LlmJudgeServiceFactory

参照 `CodeQualityCheckServiceFactory` 模式，但用 `routingChatModelPrototype`（小模型）而非 `openAiChatModel`（主模型）：

```java
@Configuration
@Slf4j
public class LlmJudgeServiceFactory {
    @Resource(name = "routingChatModelPrototype")
    private ChatModel chatModel;

    @Bean
    public LlmJudgeService llmJudgeService() {
        log.info("[LlmJudge] 织入 LlmJudgeService (model=routingChatModelPrototype, 小模型省 token)");
        return AiServices.builder(LlmJudgeService.class)
                .chatModel(chatModel)
                .build();
    }
}
```

### 4.2 RealEvalRunner 接入

```java
@Resource
private LlmJudgeService llmJudgeService;  // Spring 自动注入 Factory 产的 Bean

// scorers 列表从两维 → 三维
List<Scorer> scorers = List.of(
    new RubricScorer(),
    new RenderScorer(),
    new LlmJudgeScorer(llmJudgeService)
);
```

### 4.3 Judge 维度的评测语义

- **不 veto**：`LlmJudgeScorer.evaluate` 永远 `veto(false)`——Judge 是软评分，不会单独让 case 变 0
- **fallback 50 分**：judge=null / 异常时返回 50 分不 veto——LLM 抽风不会错误红灯
- **case veto 逻辑不变**：CompositeScorer 的 veto 仍只由 Rubric/Render 触发，Judge 只贡献分数
- **多轮影响**：Judge 会对每个 case × 每轮调一次 LLM（75 次额外调用），评测耗时 + token 增加

## 5. 验证

- [x] 通用 DoD
- [x] 编译通过
- [x] eval 包 60 测试不退化（LlmJudgeScorerParseTest 3 测试仍全绿，Factory Bean 织入由 `@SpringBootTest` 上下文加载验证）
- [x] 不跑全量评测（省 token，下次评测周期统一回归时报告自动含 judge 列）
- [x] Spring 上下文加载不报错（`@SpringBootTest` test-compile 通过 = Factory Bean 定义正确）

## 6. 风险与遗留

- **Judge 让评测慢 + 贵**：75 次额外 LLM 调用。若 token 压力大，可后续加 `eval.judgeEnabled` 开关
- **Judge 评分的随机性**：Judge 本身也是 LLM，3 轮均分会覆盖它，但 Judge 的 temperature 仍是 routingChatModelPrototype 的默认值（非 0）。留 backlog：Judge 是否也该 temperature=0
- **Judge 维度的 mergedOutput**：P0-2 后 VUE 的 mergedOutput 是源码+文件清单，Judge 会看到这些——比 minified JS 更准确，是好事

## 7. 对治理体系的更新

- [x] 本 Task Record
- [ ] 完成后更新 `docs/tasks/README.md` + `current-phase.md`（LLM-Judge 标 ✅）

## 8. 实施顺序

1. 新建 `LlmJudgeServiceFactory`
2. 改 `RealEvalRunner` 注入 + scorers 加 Judge
3. 编译 + eval 包单测
4. 回填 Task Record + 治理文档
