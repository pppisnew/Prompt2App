# Router Design

> **状态**：v1（Phase 4 落地）
> **对应 Phase**：Phase 4 · AI Router 重写
> **相关 ADR**：[ADR-0003](../adr/0003-ai-router-two-layer.md)

---

## 两层路由架构

```
userPrompt
   │
   ├──► [Layer 1] RuleRouter (毫秒级，零成本)
   │      │
   │      ├─ MULTI_FILE 关键词 (官网/网站/N 页/N 个页面/导航跳转/...)
   │      ├─ MULTI_FILE 数字模式正则  \d+\s*[个]?页(?:面)?
   │      ├─ VUE 强关键词 (vue/应用/dashboard/看板/编辑器/番茄钟/...)
   │      ├─ HTML 短小判定 (<50 字 + 无复杂关键词)
   │      └─ HTML 单页关键词 (单页/简历/邀请函/404/coming soon/...)
   │
   │      命中 → RoutingDecision(layer=RULE_*, confidence=1.0, durationMs<5)
   │      未命中 → ↓
   │
   └──► [Layer 2] LLM Fallback (AiCodeGenTypeRoutingService)
          │
          ├─ 正常 → RoutingDecision(layer=LLM_FALLBACK, confidence=0.6)
          └─ 异常 → RoutingDecision(layer=LLM_ERROR_FALLBACK,
                                    strategy=HTML, confidence=0.3)
```

## 类设计

```
router/
├── AiCodeGenTypeRoutingService.java         (existing, Layer 2 LLM 接口)
├── AiCodeGenTypeRoutingServiceFactory.java  (existing, Layer 2 工厂)
├── RoutingDecision.java                     (NEW, Value Object)
├── RuleRouter.java                          (NEW, Layer 1)
└── RoutingService.java                      (NEW, @Service 编排)
```

[`RoutingDecision`](../../src/main/java/com/prompt2app/router/RoutingDecision.java) 字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `strategy` | `CodeGenTypeEnum` | 最终选中的策略 |
| `layer` | `Layer` enum | 决策来源（RULE_KEYWORD / RULE_LENGTH / LLM_FALLBACK / LLM_ERROR_FALLBACK） |
| `reason` | `String` | 自然语言理由（"matched VUE keyword '看板'"） |
| `confidence` | `double` | 置信度 1.0 / 0.6 / 0.3 |
| `durationMs` | `long` | 端到端耗时 |
| `userPrompt` | `String` | 原始 prompt（Phase 6 入表时可截断） |

## 路由优先级（v1）

**关键设计**：MULTI_FILE 检查 **先于** VUE，因为多页信号词（官网/N 页/导航）比 VUE 弱信号词（表单/编辑器）更具体。

```
1. MULTI_FILE 关键词列表    (15 词)
2. MULTI_FILE 数字正则      \d+\s*[个]?页(?:面)?
3. VUE 关键词列表           (35 词，移除"工具/倒计时/番茄"等过于通用的)
4. HTML 短小判定            len < 50 + 无关键词
5. HTML 单页关键词列表      (~14 词，移除"简介"等过于通用的)
6. 未命中 → Layer 2 LLM
```

## 实测准确率（25 case 评测集）

| 指标 | 实测值 | 阈值 | 评价 |
| --- | --- | --- | --- |
| **命中率**（Layer 1 决定） | **88.0%** (22/25) | ≥ 80% | ✅ 通过 |
| **准确率**（命中且策略正确） | **80.0%** (20/25) | ≥ 60% | ✅ 大幅超过阈值 |
| LLM Fallback 次数 | 3/25 | — | 健康（pricing / recipe / university） |

详细分布表见 [`docs/tasks/2026-06-18-phase4-router.md`](../tasks/2026-06-18-phase4-router.md)。

> **为什么不追求 100%**：100% 准确率说明规则**过拟合**当前 25 case；实际生产分布不会与评测集完全一致。LLM fallback 的存在就是为了处理长尾。当前 80% 准确 + 12% 走 fallback 是健康分布。

## 调用方式

### 主路径（AppServiceImpl）

```java
@Resource
private com.prompt2app.router.RoutingService routingService;

RoutingDecision routingDecision = routingService.route(initPrompt);
CodeGenTypeEnum selectedCodeGenType = routingDecision.getStrategy();
log.info("路由层: {}, 耗时: {}ms",
        routingDecision.getLayer(), routingDecision.getDurationMs());
```

### Workflow 路径（RouterNode）

```java
RoutingService routingService = SpringContextUtil.getBean(RoutingService.class);
RoutingDecision decision = routingService.route(context.getOriginalPrompt());
context.setGenerationType(decision.getStrategy());
```

## 测试覆盖

| 测试类 | @Test | 覆盖 |
| --- | --- | --- |
| `RuleRouterTest` | 10 | 每条规则一个 case + 优先级 + 大小写不敏感 + null/empty + 字段完整性 |
| `RoutingServiceTest` | 6 | 规则命中不调 LLM / 规则未命中走 LLM / LLM 异常走 HTML / LLM 返回 null / 耗时字段 / 便捷方法 |
| `RouterAccuracyTest` | 1 | 25 eval case 回归：hit rate ≥ 80%、accuracy ≥ 60% |

CI 命令：`mvn test -Dtest='com.prompt2app.router.*Test'`

## 演进锚点

- 命中率 < 70% 或准确率 < 50% → 启动语义路由（embedding + 余弦距离）
- LLM fallback 出错率 > 5% → 加 retry / 多模型仲裁
- 评测集扩到 100+ case → 重新调优规则权重
- 加入 Phase 6 metric 看板后，按"误判类型 × 用户反馈"针对性扩规则

## 参考

- [ADR-0003](../adr/0003-ai-router-two-layer.md) 决策论证
- [PROJECT_CHARTER.md](../../PROJECT_CHARTER.md) §2 「核心能力 P0：AI Router」
