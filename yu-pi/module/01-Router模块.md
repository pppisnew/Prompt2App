# 模块导读 · 01 · Router 模块（AI 路由）

> 这是项目最核心的入口分类器。读完本文你能回答：prompt 怎么决定走 HTML / 多文件 / Vue。

---

## 一、模块职责

**决定用户 prompt 走哪种代码生成策略。**

输入：自然语言 prompt（如"做一个咖啡店官网"）
输出：`RoutingDecision`（含策略 + 路由层 + 理由 + 置信度 + 耗时）

**为什么需要它**：
- 三种生成策略（HTML / MULTI_FILE / VUE_PROJECT）的 prompt 模板、Tool 配置、产物结构完全不同
- 如果全用 LLM 分类：慢（秒级）+ 贵（每次 token）+ 黑盒
- 如果全用规则：覆盖不全，长尾 prompt 分错

→ **两层架构**：规则先尝试（毫秒级零成本），未命中才走 LLM 兜底

---

## 二、核心类

| 文件路径 | 类名 | 作用 |
|---|---|---|
| `router/RoutingService.java` | `RoutingService` | **编排器**：先规则后 LLM，最终兜底 HTML |
| `router/RuleRouter.java` | `RuleRouter` | **Layer 1**：关键词 + 正则规则 |
| `router/AiCodeGenTypeRoutingService.java` | `AiCodeGenTypeRoutingService` | **Layer 2**：LangChain4j 接口，LLM 兜底 |
| `router/AiCodeGenTypeRoutingServiceFactory.java` | `...Factory` | LLM 服务工厂（用 `routingChatModelPrototype` Bean）|
| `router/RoutingDecision.java` | `RoutingDecision` | 决策结果值对象（含 Layer 枚举）|

---

## 三、核心流程（文字流程图）

```
userPrompt 进入
   │
   ▼
RoutingService.route(prompt)
   │
   ├─[1] Layer 1: RuleRouter.route(prompt)
   │       │
   │       ├─ MULTI_FILE 关键词命中?  → 返回 MULTI_FILE (RULE_KEYWORD)
   │       ├─ MULTI_FILE 正则命中?    → 返回 MULTI_FILE (RULE_KEYWORD)
   │       ├─ VUE 关键词命中?         → 返回 VUE_PROJECT (RULE_KEYWORD)
   │       ├─ HTML 短小判定?          → 返回 HTML (RULE_LENGTH)
   │       └─ 都不命中                 → Optional.empty()
   │
   │  命中 → withDuration() 填耗时 → 返回 (confidence=1.0)
   │
   ├─[2] Layer 2: llmFactory.create().routeCodeGenType(prompt)
   │       │
   │       ├─ LangChain4j @SystemMessage + 结构化输出
   │       ├─ 返回 CodeGenTypeEnum
   │       └─ 包装成 RoutingDecision (LLM_FALLBACK, confidence=0.6)
   │
   └─[3] 最终兜底: LLM 异常 → HTML (LLM_ERROR_FALLBACK, confidence=0.3)
```

### Layer 1 规则细节（`RuleRouter.java`）

按优先级短路，**4 层判断 + 1 个正则**：

| 优先级 | 规则 | 触发条件 | 输出 |
|---|---|---|---|
| 1 | MULTI_FILE 关键词 | 含 `多页/官网/网站/导航跳转/sitemap` 等 ~13 词 | MULTI_FILE |
| 2 | MULTI_FILE 正则 | `\d+\s*[个]?页(?:面)?`（如"5 个页面"）| MULTI_FILE |
| 3 | VUE 关键词 | 含 `应用/看板/仪表盘/编辑器/番茄钟/待办/表单/拖拽/游戏` 等 ~30 词 | VUE_PROJECT |
| 4 | HTML 短小判定 | prompt < 50 字 且未命中前 3 条 | HTML |
| 5 | HTML 单页关键词 | 含 `单页/卡片/简历/名片/邀请函/海报/404` 且 < 200 字 | HTML |
| - | 都不命中 | - | `Optional.empty()` → Layer 2 |

**关键设计：MULTI_FILE 优先于 VUE**
- 因为 "含表单的多页官网" 两边都能匹配
- 但 MULTI_FILE 是**结构信号**（多页），VUE 是**组件信号**（表单）
- 结构信号更具体 → 优先

**调优记录**（代码注释里有）：
- "倒计时" → "倒计时器"（避免 case 007 coming-soon-launch 误判）
- "番茄" → "番茄钟"
- "状态" → "状态管理"
- 删除 "简介"（太通用，case 015 大学系简介误判）

### Layer 2 LLM 兜底

```java
@SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
CodeGenTypeEnum routeCodeGenType(String userPrompt);
```
- LangChain4j 的 `AiServices` 把 LLM 返回**直接映射成枚举**（结构化输出）
- System Prompt 在 `resources/prompt/codegen-routing-system-prompt.txt`
- 用独立的 `routingChatModelPrototype` Bean（可配不同模型/参数）

---

## 四、RoutingDecision 值对象

```java
@Value @Builder
public class RoutingDecision {
    enum Layer {
        RULE_KEYWORD,        // Layer 1 关键词命中
        RULE_LENGTH,         // Layer 1 字数判定
        LLM_FALLBACK,        // Layer 2 LLM 正常返回
        LLM_ERROR_FALLBACK   // LLM 异常退到 HTML
    }
    CodeGenTypeEnum strategy;   // 最终策略
    Layer layer;                // 由哪层决策
    String reason;              // 决策理由（如 "matched VUE keyword '看板'"）
    double confidence;          // 置信度: 规则=1.0 / LLM=0.6 / 异常=0.3
    long durationMs;            // 端到端耗时
    String userPrompt;          // 原始 prompt（溯源）
}
```

**为什么设计成值对象**：
- 不可变（`@Value`）
- 携带元数据 → 可落 `generation_metric` 表 / 写日志 / 评测分析
- 不仅是"选哪个策略"，还有"为什么选 + 多快 + 多可信"

---

## 五、阅读顺序（想读懂本模块按这个顺序）

1. **先看 `RoutingDecision.java`** — 理解输出结构（值对象，简单）
2. **再看 `RuleRouter.java`** — 理解 Layer 1 规则（核心，看关键词列表）
3. **再看 `AiCodeGenTypeRoutingService.java`** — 理解 Layer 2（就一个接口，看注解）
4. **最后看 `RoutingService.java`** — 理解编排（把上面串起来）
5. **配置看 `AiCodeGenTypeRoutingServiceFactory.java`** — LLM Bean 怎么来的

---

## 六、设计模式

### 1. 责任链模式（Chain of Responsibility）变体
- Layer 1 → Layer 2 → 最终兜底，依次尝试
- 每层决定"我处理 or 传给下一层"
- 区别于经典责任链：这里是**降级链**（规则不行→LLM→HTML），不是平等传递

### 2. 策略模式（隐含）
- `CodeGenTypeEnum` 三种策略，下游 `AiCodeGeneratorFacade` 用 switch 分派
- Router 本身不实现策略，只**选择策略**

### 3. 值对象模式
- `RoutingDecision` 不可变，携带完整决策上下文

---

## 七、为什么这样设计（设计原因）

### Q1：为什么不全用 LLM 分类？
- **成本**：每次创建应用都要调一次 LLM，token 烧不起
- **延迟**：秒级 vs 毫秒级
- **可观测**：规则可调试，LLM 黑盒
- **稳定**：规则确定性高，LLM 同一 prompt 可能返回不同结果

### Q2：为什么不全用规则？
- **覆盖率**：长尾 prompt 规则覆盖不全
- **维护**：规则越多越难维护，且容易过拟合评测集
- **所以才留 LLM 兜底**：规则未命中走 LLM，保证 100% 有决策

### Q3：为什么置信度规则=1.0 / LLM=0.6 / 异常=0.3？
- 规则：确定性判断，要么命中要么不命中，置信度高
- LLM：概率模型，有不确定性
- 异常：明显不可信
- 这个值用于 `generation_metric` 表分析，不是硬性阈值

### Q4：为什么 MULTI_FILE 优先于 VUE？
- 见上"关键设计"：结构信号 > 组件信号
- "多页官网含表单"应走 MULTI_FILE，不是 VUE

### Q5：为什么路由决策只在创建应用时做一次？
- prompt 不变 → 策略不变
- 避免每次对话都重新路由（成本 + 不一致）
- 写入 `app.code_gen_type` 字段持久化

---

## 八、关联代码

**谁调用 Router**：
- `AppServiceImpl.createApp()` → `routingService.route(initPrompt)`

**Router 调用谁**：
- `RuleRouter`（内部纯 Java，无外部依赖）
- `AiCodeGenTypeRoutingService`（调 LangChain4j → LLM API）

**Router 的产物去哪**：
- `RoutingDecision.getStrategy()` → 写入 `app.code_gen_type`
- `RoutingDecision` 整体 → `generationMetricService.recordRouting()` 落监控表

---

## 九、常见面试追问

**Q：规则层准确率 80% / 命中率 88% 怎么测的？**
A：`RouterAccuracyTest` 跑 25 case，`MIN_ACCURACY=0.6` / `MIN_HIT_RATE=0.8` 是下限阈值（避免过拟合），实际跑出 80%/88%。

**Q：为什么不追求 100% 准确率？**
A：100% 意味着给每个 case 量身定做规则，规则膨胀到不可维护，新 prompt 照样错。60% 下限留 LLM fallback 空间，这才是两层架构的意义。

**Q：LLM 兜底的延迟和成本？**
A：只有 ~12% prompt 走 LLM（88% 命中规则）。LLM 用独立 `routingChatModelPrototype`，可配便宜模型。

---

## 🎯 知识检查

请回答以下三个问题，答完我再给反馈，再进入下一个模块：

**【问题 1】Router 模块负责什么？为什么需要两层而不是一层？**

**【问题 2】为什么 MULTI_FILE 关键词的优先级高于 VUE 关键词？如果反过来会怎样？**

**【问题 3】如果删除 Layer 2（LLM 兜底），直接让规则未命中返回 HTML，会怎样？**

---

> 下一份：`02-Agent模块.md`（AI 生成 + Tool 安全，最重要）
