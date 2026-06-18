# ADR-0003：AI Router 采用规则 + LLM 兜底两层架构

- **状态**：Accepted
- **日期**：2026-06-18
- **决策者**：项目作者
- **相关 Phase**：Phase 4 · AI Router 重写
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

项目支持三种代码生成策略：`HTML` / `MULTI_FILE` / `VUE_PROJECT`。每种策略的成本与适用场景差距明显：

| 策略 | 适合场景 | 单次 Token 成本（DeepSeek 估算） | 单次耗时 |
| --- | --- | --- | --- |
| `HTML` | 单页网站、简短需求（< 50 字） | ~$0.001 | 5-15s |
| `MULTI_FILE` | 多页静态站、中等复杂度 | ~$0.01 | 15-40s |
| `VUE_PROJECT` | 含交互/状态的应用 | ~$0.04 | 60-180s（多轮 tool call） |

### 教学版的现状

```java
// AiCodeGenTypeRoutingService（接口）
@SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
CodeGenTypeEnum routeCodeGenType(String userPrompt);
```

每次都丢给小 LLM 模型分类。事实判断：

- **每个用户请求都至少多一次 LLM round-trip**（即使是"做一个 hello world"也要先调 LLM 判断）
- **路由错误的代价不对称**：
  - HTML → 应是 Vue：用户一看就知道功能没实现，容易报告
  - Vue → 应是 HTML：用户得到了一个 Vue 工程当落地页，体验割裂；成本却是 40 倍
- **无可观测性**：路由"为什么这么选"对外是黑盒；只能事后看 LLM 输出
- **无成本意识**：路由本身的 LLM 调用 + 后续策略 LLM 调用 = 双倍 token

### 业界经验

短输入的分类问题用大模型属于"大炮打蚊子"。生产级 AI 路由通常是：
- **先规则**：覆盖 80%+ 的高置信度短尾
- **再 LLM**：兜底长尾、不确定情形
- **再 fallback**：LLM 超时/error 时退到最便宜策略

---

## 备选方案（Options）

### 方案 A：保留单层 LLM 路由

- 优点：实现最简单，已经在跑
- 缺点：见上文「事实判断」3 条
- 长期成本：每个请求多 1 次 LLM 调用，路由错误无可观测性
- **本质问题**：把毫秒级的分类问题用秒级的 LLM 解决

### 方案 B：纯规则路由（无 LLM）

- 优点：零延迟、零成本、100% 可解释
- 缺点：
  - 规则覆盖不到的长尾请求（"我要一个能让我妈用的产品"）只能 fallback 到默认策略，可能错得离谱
  - 规则维护成本随 prompt 多样性增长
- 长期成本：低延迟，但召回率低

### 方案 C：规则 + LLM 兜底两层（**本决策**）

- **Layer 1**：纯字符串规则
  - 关键词（"应用 / 看板 / 编辑器" → VUE，"多页 / 官网" → MULTI_FILE）
  - 字数（< 50 字 + 无复杂关键词 → HTML）
  - 命中即返回 `RoutingDecision(layer=RULE_*, confidence=1.0)`
- **Layer 2**：LLM 兜底
  - 仅 Layer 1 未命中时调用
  - 复用现有 `AiCodeGenTypeRoutingService`（不重写）
  - 返回 `RoutingDecision(layer=LLM_FALLBACK, confidence=0.6)`
- **最终兜底**：LLM 失败时返回 HTML（最便宜策略）+ `confidence=0.3`

- 优点：
  - 90%+ 简单请求毫秒级出结果
  - 长尾请求仍有 LLM 兜底
  - 每次决策带 `RoutingDecision { strategy, layer, reason, confidence }`，**Phase 6 直接落表做"路由准确率"分析**
  - 简历可以讲："9 成走规则、长尾走 LLM"——这是生产级 AI Engineering 的标志
- 缺点：
  - Layer 1 规则需要维护
  - 引入"层"的概念有学习成本

### 方案 D：纯 LLM 但用更小的模型

- 优点：成本可降低到 1/10
- 缺点：仍是每次 round-trip，没解决"延迟"问题
- 长期成本：还是单层 LLM 思路

---

## 决策（Decision）

**选择方案 C**：规则 + LLM 兜底两层架构。

### 实施细节

#### 数据结构

```java
@Value @Builder
class RoutingDecision {
    CodeGenTypeEnum strategy;   // HTML / MULTI_FILE / VUE_PROJECT
    Layer layer;                // RULE_KEYWORD / RULE_LENGTH / LLM_FALLBACK / LLM_ERROR_FALLBACK
    String reason;              // "matched VUE keyword '看板'"
    double confidence;          // 1.0 / 0.6 / 0.3
    long durationMs;            // 端到端
}
enum Layer {
    RULE_KEYWORD,        // Layer 1 关键词命中
    RULE_LENGTH,         // Layer 1 字数命中
    LLM_FALLBACK,        // Layer 2 LLM 正常出口
    LLM_ERROR_FALLBACK   // Layer 2 LLM 异常 → HTML 兜底
}
```

#### 类设计

```
router/
├── AiCodeGenTypeRoutingService.java         (existing, 保留作 Layer 2)
├── AiCodeGenTypeRoutingServiceFactory.java  (existing)
├── RoutingDecision.java                     (NEW, value object)
├── RuleRouter.java                          (NEW, Layer 1)
└── RoutingService.java                      (NEW, orchestrator @Service)
```

#### Layer 1 规则集（v1）

按优先级从高到低试探：

1. **VUE 强关键词**（任一命中 → VUE_PROJECT）：
   ```
   应用 / app / 工具 / dashboard / 仪表盘 / 看板 / kanban /
   编辑器 / 计时 / 番茄 / 倒计时 / 待办 / todo /
   交互 / 状态 / 表单 / 拖拽 / 持久化 / localStorage /
   游戏 / quiz / 棋 / 计算器 / 记账 / 搜索（搭配筛选）
   ```

2. **MULTI_FILE 关键词**（任一命中 → MULTI_FILE）：
   ```
   多页 / 多个页面 / 包含 N 页 / 官网 / 网站 / 主页 / 落地页（≥3 页时）/
   导航跳转 / sitemap
   ```

3. **HTML 短小判定**（字数 < 50 且无上述关键词 → HTML）

4. **HTML 单页关键词**（任一命中且字数 < 200 → HTML）：
   ```
   单页 / 一页 / 页面 / 卡片 / 简介 / 简历 / 名片 /
   邀请函 / 海报 / 落地页（单页时）
   ```

5. **未命中 → return Optional.empty() → 走 Layer 2**

> 规则在代码中以"按顺序短路"实现。详见 `RuleRouter.java`。

#### 调用方式

```java
// AppServiceImpl
@Resource
private RoutingService routingService;

// 替换原 aiCodeGenTypeRoutingServiceFactory.create...routeCodeGenType(...) 调用
RoutingDecision decision = routingService.route(initPrompt);
CodeGenTypeEnum strategy = decision.getStrategy();
log.info("[Router] decision={}", decision);  // Phase 6 改为入表
```

#### 测试覆盖

| 测试 | 数量 | 覆盖 |
| --- | --- | --- |
| `RuleRouterTest` | ~10 | 每条规则一个 case + 边界 + 未命中 |
| `RoutingServiceTest` | ~5 | 规则命中直接返回 / 规则未命中走 LLM / LLM 异常走 HTML 兜底 / RoutingDecision 字段完整性 |
| `RouterAccuracyTest` | 1 | 25 eval case × `expected_strategy` 对齐验证：Layer 1 准确率 ≥ 60%（不要求 100%，否则规则会过拟合 case） |

---

## 代价（Consequences）

### 正面

- **延迟**：90%+ 请求毫秒级（绕过 LLM round-trip）
- **成本**：路由阶段 token 节省 90%+
- **可观测性**：每个请求都有 `RoutingDecision`，Phase 6 直接做"路由准确率"看板
- **故障容忍**：LLM 模型挂了也有 HTML 兜底，不阻塞用户
- **简历**：从"我加了一个 LLM 调用"升级到"我设计了规则 + LLM 兜底两层路由，9 成走规则"

### 负面

- **规则维护成本**：随时间需要根据 production 数据回调阈值。现在用 25 case 兜底
- **规则可能过拟合 25 case**：因此 Layer 1 准确率目标设 60%（而不是 100%），留 LLM fallback 空间
- **`RoutingDecision` 暂不入表**：仅日志，Phase 6 接入

### 中性

- 现有 `AiCodeGenTypeRoutingService` 接口完全保留，仅成为 Layer 2，不影响其使用方（如 `RouterNode`）
- 现有 `prompt/codegen-routing-system-prompt.txt` 不动

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估：

- Layer 1 命中率 < 70%（说明规则覆盖太窄，需要扩充关键词或加入语义层）
- LLM fallback 出错率 > 5%（需要更可靠的兜底策略）
- Phase 6 入表后发现"路由错误"用户反馈集中（需要按错误类型针对性扩规则）
- 评测集扩到 100+ case 后 Layer 1 准确率下降 → 启动语义路由（embedding + 距离）

---

## 关于"为什么不直接用 embedding 路由"

> 一种更现代的方案：把 prompt 转 embedding，与每个策略的中心点做余弦相似度。

短期 NO：
- 需要引入 embedding 模型（增加依赖、成本）
- 25 case 不足以建立稳定的策略中心点
- 关键词规则已能覆盖 90% 短尾，性价比高

未来 YES（评估条件）：
- 评测集 ≥ 100 case 且关键词规则准确率开始下降
- 已经接入 embedding 模型用于 RAG（边际成本低）

到时另写 ADR-0003b。

---

## 参考资料（References）

- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §2 「核心能力 P0：AI Router」
- [`docs/architecture/router-design.md`](../architecture/router-design.md) ← 本 ADR 落地后从 stub 升到 v1
- [`eval/cases/`](../../eval/cases/) 25 case，每条带 `expected_strategy`，作为 Layer 1 准确率回归基准
- 业界参考：[Latitude.so AI Routing Patterns](https://docs.latitude.so/guides/cookbook/router)、[OpenAI ChatModelRouter](https://github.com/openai/openai-cookbook)
