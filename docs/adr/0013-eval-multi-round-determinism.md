# ADR-0013：评测改为多轮均分 + temperature=0 + 断点续跑

- **状态**：Accepted
- **日期**：2026-06-22
- **决策者**：项目作者 + AI
- **相关 Phase**：Eval 增量
- **相关 Task**：[`2026-06-22-eval-multi-round-determinism.md`](../tasks/2026-06-22-eval-multi-round-determinism.md)（待建）
- **延续 ADR**：[ADR-0005](./0005-evaluation-automation.md)（评测三维体系）/ [ADR-0008](./0008-evaluation-first.md)（评测先行）

---

## 背景（Context）

ADR-0005 落地的评测体系是**单次跑**（每个 case 调用 LLM 一次，三维评分，总均分一份报告）。前两轮 Eval 增量 task（2026-06-21 / 06-22）暴露出一个之前未量化的问题：

- **LLM 抽样波动远超预期**：同一份 25 case 评测集、同一份代码、同一份配置，两次跑总均分相差 **19.68 分**（55.06 → 35.38）。
- 实测归因（[task 2026-06-21-vue-render-build-observability §5.4](../tasks/2026-06-21-vue-render-build-observability.md)）：
  - 4 个 case 的 Render veto 是 LLM 这次生成的 HTML body 文本 < 100 字符
  - 1 个 case 是 LLM `TimeoutException` 网络抖
  - 3 个 case 是 LLM 输出缺 `must_contain` 关键词
  - **无一是代码引入**——`RenderScorer.checkHtml` / `checkMultiFile` 本次完全未改动，单测 39/39 全绿。
- **后果**：Charter §4 "评测回归 >5% 不合入" 红线被随机性触发，无法区分"真实代码回归"和"LLM 抽样波动"。每次评测后都要手工逐 case 实测归因，治理成本高。
- 项目目前评测均分仍处于 35-55 区间，方差吃掉了大量真实修复信号。

---

## 备选方案（Options）

### 方案 A：保持单次跑，依赖手工归因

- 优点：零改动、零额外 token 消耗
- 缺点：
  - 每次评测后都要逐 case 实测才能判定"真回归 vs 假回归"，治理成本不可持续
  - 简历/作品集叙述时分数不可信（单次跑 ±20 分波动）
  - Charter 红线名存实亡
- 一次性成本：0
- 长期成本：高（每次评测人工归因）

### 方案 B：仅设 `temperature=0`，不改跑次

- 优点：实施最小，理论上减小随机性
- 缺点：
  - 实测 OpenAI 系列 API `temperature=0` 并非完全确定性（top-p、模型采样实现细节仍有微抖）
  - 无法用统计手段量化剩余方差
  - 单次跑无法给出"标准差"
- 一次性成本：极小（几行代码）
- 长期成本：中（仍需归因，但波动可能从 ±20 降到 ±10）

### 方案 C：保留默认 temperature，多轮跑取均分

- 优点：接近真实生产分布、用统计抑制方差
- 缺点：
  - 单跑 token 翻倍（3 轮 = 3×）
  - 仍需要稳定的"基线"概念，否则均分/方差仍漂移
- 一次性成本：中（评测 runner 改造 + 报告格式扩展）
- 长期成本：低（评测结果可统计）

### 方案 D（推荐）：`temperature=0` + 多轮均分（3 轮）+ 断点续跑 + 报告含均分/标准差/每轮分数

- 优点：
  - 双保险：尽量去随机 + 多轮统计兜底
  - 3 轮是学术评测常用平衡点（方差消除 vs 成本）
  - 断点续跑：单轮失败可单独补跑，3 小时长跑不至于全废
  - 报告含标准差：可量化方差、可写入简历/作品集
- 缺点：
  - token 消耗 3×（每次评测 ~3 小时 + 3× token）
  - 评测 runner 改造工作量大于方案 B/C
  - 多轮均分对 `must_contain` 这类 hard-rubric 的 0/100 跳变仍敏感（3 轮里有 1 轮缺词就拉低 33 分均分）
- 一次性成本：1 天（runner 改造 + 报告格式 + 断点续跑机制）
- 长期成本：低（评测后无需手工归因；结果可发表）

---

## 决策（Decision）

**选择方案 D**：评测改为 **`temperature=0` + 3 轮跑取均分 + 断点续跑 + 报告含均分/标准差/每轮分数**。

### 落地细节

1. **temperature 来源**：通过 `Prompt2AppProperties` 暴露 `eval.temperature`（默认 `0`），ChatModel 构建时读取该值。生产路径（非评测）保持默认 `temperature=0.7`，**不影响 Phase 1 业务**。
2. **轮数**：默认 3 轮，可通过 `eval.rounds`（默认 3）配置。
3. **断点续跑**：
   - 每轮独立产物目录：`tmp/code_output/round-{N}/<strategy>_<appId>/`
   - 每轮独立中间报告：`eval/reports/runs/baseline-real.round-{N}.md`
   - 聚合脚本/方法读各轮中间报告→均分/标准差→`eval/reports/baseline-real.md`
   - 若某轮中途失败，重跑该轮（识别 `round-{N}` 已完成 case 跳过 LLM 调用）
4. **报告格式**：
   - 顶部表：每 case 三轮分数 + 均分 + 标准差
   - 中部：维度均分（HTML/MULTI_FILE/VUE_PROJECT × 三轮 + 均分）
   - 底部：总均分（含均分 ± 标准差）+ 与 prev baseline diff
5. **回归判定（Charter §4 红线复用）**：
   - "评测回归 >5% 不合入"改为基于 **3 轮均分总分** 比较
   - 单轮波动不再触发红线
6. **不动评测契约的核心**：`InvocationResult` / `Scorer` / case schema 全部不变，只在 runner 编排层加多轮 + 聚合

### 关键约束

- **作品集场景**：单人维护、追求评测分数稳定性 > 评测耗时
- **token 预算可承受 3×**：当前评测每轮 ~1k 调用，3 轮 = 3k，单次评测成本可控
- **评测目标是衡量代码改动效果**，不是测 LLM 模型本身——多轮均分恰好把"LLM 抽样"作为噪声平均掉
- **temperature=0 不是完全确定性**：实测 OpenAI API 在 temperature=0 时仍有微小不确定性（推测：浮点累加顺序 + 模型并行实现），所以仍需多轮

---

## 代价（Consequences）

### 正面

- 评测分数稳定，**Charter §4 "评测回归 >5%" 红线重新可信**
- 报告含标准差，可量化"修复确定有效"vs"波动看起来有效"
- 单轮失败不再废一整次评测（断点续跑）
- 作品集/简历叙述可写"3 轮均分 X ± Y 分"，专业可信
- 与学术评测惯例对齐（多 seed 取均分）

### 负面

- **每次评测 3× token 成本 + 3× 时间**（~3 小时连续跑）
- 评测 runner 编排层增加复杂度（多轮 + 聚合 + 续跑状态）
- 多轮均分对 `must_contain` 0/100 跳变仍敏感——根因 A 类问题需独立 task 治理
- baseline-real.md 不再向后兼容（旧 stub 报告读取需迁移或废弃）
- 历史 baseline（19.75 / 55.06 / 35.38 单次分）失去严格可比性——需在报告里标注"v1 单次跑 → v2 三轮均分"

### 中性（影响未明）

- temperature=0 可能让 LLM 输出"卡"在某种风格，对部分 case 反而比 0.7 分更低——3 轮也无法消除这种系统性偏移
- `LlmJudgeScorer`（待启用）本身也是 LLM 调用，3 轮均分会让 Judge 调用也 3×。Judge 维度的 temperature/轮数是否独立？留遗留议题

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估本决策：

- **3 轮均分的标准差仍 > 8 分**：说明 temperature=0 + 3 轮仍未抑制方差，需上 5 轮或重新评估 case 设计
- **token 预算被压垮**：单月评测开支显著影响项目可持续性
- **评测耗时 > 1 天**：单轮 3 小时 × 3 轮 + npm build 可能撑爆——届时考虑并发跑（但需评估并发对 npm 共享缓存的影响）
- **`temperature=0` 让 baseline 显著下降**：某些 case 在 0.7 下能过、0 下过不了，说明对 LLM 风格敏感——需要 case 容错性升级
- **评测改为生产质量监控**：若评测从"作品集衡量"升级为"线上服务的回归门"，可能需要每次发布前 5 轮 + 多 seed

---

## 参考资料（References）

- [ADR-0005](./0005-evaluation-automation.md) §决策：三维评分体系（本 ADR 不改变三维评分，只改运行模式）
- [ADR-0008](./0008-evaluation-first.md) §决策：评测先行（本 ADR 是评测先行原则下的运行可信性补强）
- [Task: VUE Render 修复 §5.4](../tasks/2026-06-21-vue-render-build-observability.md) - 逐 case 归因实证，触发本 ADR 的直接原因
- [`eval/reports/baseline-real.55.06.bak.md`](../../eval/reports/baseline-real.55.06.bak.md) / [`eval/reports/baseline-real.md`](../../eval/reports/baseline-real.md) - 两次单跑 19.68 分波动的实证
- Anthropic Cookbook "Eval with Claude" / OpenAI Evals - 多 seed 取均分是学术界/工业界 LLM eval 通行做法
