# Task: 补齐评测集 v0 至 25 条 case

- **日期**：2026-06-18
- **Phase**：Phase 0 · 评测基线（主线推进）
- **责任人**：项目作者（指令）+ AI 助手（执行）
- **状态**：Done
- **关联 ADR**：[ADR-0008](../adr/0008-evaluation-first.md)

---

## 1. 目标

把评测集从 3 条示范扩到 25 条满配，达到 Phase 0 DoD 中"≥25 case 且按 HTML 7 / MultiFile 8 / Vue 10 分布"的要求。

## 2. 背景

ADR-0009 项目重命名完成后回到 Phase 0 主线。评测执行器 + baseline 报告需要 case 数据作为输入，所以"补满 case"是评测体系上线的最关键前置工作。

## 3. 影响范围（Scope）

- **代码模块**：无（Phase 0 不动业务代码）
- **数据**：新增 22 个 YAML 文件到 `eval/cases/`
- **文档**：更新 `eval/README.md` 索引、`docs/roadmap/current-phase.md` checklist、`docs/roadmap/milestones.md` 状态变更记录
- **未触碰**：src/、pom.xml、前端、ADR

## 4. 修改内容

### 4.1 新增 22 个评测 case

按 ADR-0008 §v0 范围设计，分布严格匹配 DoD：

**HTML（共 7 条 = 1 已有 + 6 新增）**
- 001-personal-resume-page（已有）
- 004-saas-landing-hero · SaaS 落地页 hero
- 005-error-404-page · 404 错误页
- 006-wedding-invitation-card · 婚礼邀请函
- 007-coming-soon-launch · 倒计时上线页
- 008-pricing-table-3-tier · 三档定价对比
- 009-recipe-card-printable · 可打印菜谱卡（含 @media print）

**MultiFile（共 8 条 = 1 已有 + 7 新增）**
- 002-coffee-shop-multipage（已有）
- 010-photographer-portfolio · 摄影师作品集
- 011-bookstore-multipage · 独立书店 5 页
- 012-tech-conference-site · 技术大会
- 013-restaurant-bistro-site · 法式餐馆（中法双语）
- 014-ngo-charity-site · 公益组织
- 015-university-cs-program · 大学计算机系
- 016-product-launch-microsite · 硬件产品发布

**Vue（共 10 条 = 1 已有 + 9 新增）**
- 003-todo-app-vue（已有）
- 017-weather-dashboard-vue · 天气仪表盘
- 018-markdown-editor-vue · Markdown 编辑器（禁用 marked/markdown-it）
- 019-pomodoro-timer-vue · 番茄钟（Web Audio API）
- 020-expense-tracker-vue · 记账本（自实现 SVG 饼图）
- 021-kanban-board-vue · Kanban 看板（HTML5 Drag&Drop）
- 022-recipe-search-vue · 菜谱搜索（多筛选条件）
- 023-quiz-app-vue · 知识竞赛
- 024-url-shortener-vue · 短链生成器（Clipboard API）
- 025-tic-tac-toe-vue · 井字棋（贪心 AI）

### 4.2 设计原则

每个 case 不只是 prompt，是**带评分意图的样本**：

- `must_contain` 抓**确定性元素**（关键字符串、HTML 标签、属性）
- `must_not_contain` 抓**反模式 + 禁止依赖**（lorem / TODO / 禁用库名）
- `llm_judge_dimensions` 抓**主观质量**（信息密度、视觉层次、状态机正确性等）
- `min_files` 控制 MultiFile / Vue 的最小产物规模
- `notes` 写下"考察什么 / 常见失败"，便于人工 review case 设计意图

### 4.3 难度分布（自然形成）

| 难度 | 数量 | 主要分布 |
| --- | --- | --- |
| easy | 7 | 全部 HTML |
| medium | 8 | 全部 MultiFile |
| hard | 10 | 全部 Vue |

> 难度恰好与策略一一对应——这是 Router 设计意图的体现：HTML 走简单 / MultiFile 走中等 / Vue 走复杂。

### 4.4 高级考察点（散布在 22 条新 case 中）

| 考察点 | 出现在 | 设计意图 |
| --- | --- | --- |
| @media print | 009 | 测试"打印样式"专门 CSS 能力 |
| 双语菜单 | 013 | 测试多语言细节 |
| Web Audio API | 019 | 禁止外部音频，必须自生成 beep |
| 自实现 SVG 饼图 | 020 | 禁止 chart.js / echarts |
| HTML5 Drag&Drop | 021 | 禁止 vuedraggable / sortablejs |
| 自实现 Markdown 解析 | 018 | 禁止 marked / markdown-it |
| 状态机（4 → 长休） | 019 | 复杂转移逻辑 |
| Clipboard API + toast | 024 | 浏览器 API 调用 |
| 贪心 AI 三档策略 | 025 | 不要过度工程化（禁 minimax） |
| URL 校验 + 错误反馈 | 024 | 表单验证细节 |

这些点是**面试时讲"我的评测集设计"的素材**——可以说"我的 22 条 case 不只是数量，每一条都嵌入了对生成器某个具体能力的考察点"。

### 4.5 治理文档同步

- `eval/README.md`：目录结构 + 进度区域全部更新
- `docs/roadmap/current-phase.md`：DoD checklist 勾选 25 case + 字段完整性
- `docs/roadmap/milestones.md`：Phase 0 关键产出从"25 case + baseline" 更新到"25 case ✅ + 重命名 ✅ + baseline 待跑"，新增 2 条状态变更记录

## 5. 验证

### 通用 DoD

- [x] 该 Phase 的 ADR 已写并 Accepted（ADR-0008）
- [x] 至少 1 篇 Task Record 已留存（本文件）
- [x] `current-phase.md` 的 Phase 0 case 部分 checklist 已勾选
- [x] `milestones.md` 状态变更记录追加
- [x] 没有遗留 `// TODO` 在主路径代码（本次纯 case 数据）

### 任务专属验证

- [x] `ls eval/cases/*.yaml | wc -l` → **25**
- [x] `expected_strategy: HTML$` → **7** ✅
- [x] `expected_strategy: MULTI_FILE$` → **8** ✅
- [x] `expected_strategy: VUE_PROJECT$` → **10** ✅
- [x] difficulty 分布 7 / 8 / 10 自然匹配 easy / medium / hard
- [x] 每条 case 均含 `must_contain` / `must_not_contain` / `llm_judge_dimensions` / `notes` 四个核心字段
- [x] 每个 Vue case 含 `min_files` 字段；MultiFile case 也均含

## 6. 风险与遗留

### 已知风险

- **case 设计未经实测**：22 条新 case 全部是 v0 设计，未经实际跑动验证。可能存在：
  - `must_contain` 太严（合理生成也会失败）
  - `must_contain` 太松（垃圾输出也能通过）
  - 反模式 / 禁用库列表不全
- **缓解**：跑通基线后会有第一份 `reports/baseline.md`，届时可以根据真实 LLM 输出回调每条 case 的 rubric。预期 v0 → v1 会调整 30-40% 的字段。

### 遗留事项

- 评测执行器实现（在 `src/main/java/com/prompt2app/eval/`）：下一个 task
- baseline.md 跑通：执行器写完后做
- LLM-Judge 提示词稳定性验证（同 case 跑 3 次方差 < 10%）：评测体系自动化时（Phase 5）做

## 7. 对治理体系的更新

- [x] 更新了 `docs/roadmap/current-phase.md` 的 case checklist
- [x] 更新了 `docs/roadmap/milestones.md` 状态变更记录
- [x] 更新了 `eval/README.md` 的目录与进度
- [x] 没有需要新增 ADR（仅是按既定方案补数据）
- [x] 没有需要新增 backlog（无意外发现）

## 8. 下一步建议

1. **commit + push** 本批 case 与文档更新。
2. **下一个任务**：写评测执行器（Phase 0 的最后一步），目标是能 `mvn eval:run` 跑通 25 case 并产出 baseline.md。
3. **不要**顺势开始 Phase 1（按 ADR-0009 §越界元规则）。

---

**Phase 0 进度**：25 case ✅ → 重命名 ✅ → 执行器 ⏳ → baseline ⏳ → Phase 0 Done
