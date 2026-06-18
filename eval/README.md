# Eval — Prompt 评测体系

> 本目录是项目的"尺子"。任何会影响 AI 生成行为的代码改动（重构、Prompt 调整、模型切换、Router 改写），都必须在改动前后跑一遍评测集，对比结果。

## 目录结构

```
eval/
├── README.md                  # 本文件
├── schema/
│   └── case.schema.yaml       # 评测 case 字段定义
├── cases/                     # 评测集 v0（25/25 ✅，HTML 7 / MultiFile 8 / Vue 10）
│   ├── 001-personal-resume-page.yaml          (HTML, easy)
│   ├── 002-coffee-shop-multipage.yaml         (MultiFile, medium)
│   ├── 003-todo-app-vue.yaml                  (Vue, hard)
│   ├── 004-saas-landing-hero.yaml             (HTML, easy)
│   ├── 005-error-404-page.yaml                (HTML, easy)
│   ├── 006-wedding-invitation-card.yaml       (HTML, easy)
│   ├── 007-coming-soon-launch.yaml            (HTML, easy)
│   ├── 008-pricing-table-3-tier.yaml          (HTML, easy)
│   ├── 009-recipe-card-printable.yaml         (HTML, easy)
│   ├── 010-photographer-portfolio.yaml        (MultiFile, medium)
│   ├── 011-bookstore-multipage.yaml           (MultiFile, medium)
│   ├── 012-tech-conference-site.yaml          (MultiFile, medium)
│   ├── 013-restaurant-bistro-site.yaml        (MultiFile, medium)
│   ├── 014-ngo-charity-site.yaml              (MultiFile, medium)
│   ├── 015-university-cs-program.yaml         (MultiFile, medium)
│   ├── 016-product-launch-microsite.yaml      (MultiFile, medium)
│   ├── 017-weather-dashboard-vue.yaml         (Vue, hard)
│   ├── 018-markdown-editor-vue.yaml           (Vue, hard)
│   ├── 019-pomodoro-timer-vue.yaml            (Vue, hard)
│   ├── 020-expense-tracker-vue.yaml           (Vue, hard)
│   ├── 021-kanban-board-vue.yaml              (Vue, hard)
│   ├── 022-recipe-search-vue.yaml             (Vue, hard)
│   ├── 023-quiz-app-vue.yaml                  (Vue, hard)
│   ├── 024-url-shortener-vue.yaml             (Vue, hard)
│   └── 025-tic-tac-toe-vue.yaml               (Vue, hard)
└── reports/                   # 每次跑评测的产物
    └── baseline.md            # （待跑）重构前的基线快照
```

## 三维评分（v0）

| 维度 | 实现 | 权重 |
| --- | --- | --- |
| **编译/构建成功** | HTML 直接尝试在 Playwright 加载；Vue 跑 `npm run build` | 否决项（任一失败直接 0 分） |
| **页面渲染非空** | Playwright 加载产物，`page.locator('body').textContent().length > N` | 否决项 |
| **LLM-as-Judge 主观分** | 把 prompt + 产物 + rubric 喂给一个评判模型，输出 0-100 分 + 简短理由 | 主分（0-100） |

> 不做视觉相似度、Lighthouse、a11y——见 [ADR-0008](../docs/adr/0008-evaluation-first.md)。

## 工作流

```
改 Prompt / Router / Agent
        ↓
  本地跑评测：mvn eval:run
        ↓
对比 baseline.md，分数下降 > 5% → 红灯
        ↓
   通过后再 commit
```

## v0 完成标准

- [x] schema 定义
- [x] 25 case（HTML 7 / MultiFile 8 / Vue 10）✅
- [ ] 评测执行器（`com.prompt2app.eval` 包，Phase 0 末尾完成）
- [ ] 跑通基线，写入 `reports/baseline.md`
- [ ] 后续每个 Phase 完成后追加一份 `reports/phase-N.md`
