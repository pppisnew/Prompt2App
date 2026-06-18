# Eval — Prompt 评测体系

> 本目录是项目的"尺子"。任何会影响 AI 生成行为的代码改动（重构、Prompt 调整、模型切换、Router 改写），都必须在改动前后跑一遍评测集，对比结果。

## 目录结构

```
eval/
├── README.md                  # 本文件
├── schema/
│   └── case.schema.yaml       # 评测 case 字段定义
├── cases/                     # 评测集（v0 目标 25 条，HTML 7 / MultiFile 8 / Vue 10）
│   ├── 001-personal-resume-page.yaml
│   ├── 002-coffee-shop-multipage.yaml
│   └── 003-todo-app-vue.yaml
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
- [x] 3 条示范 case（HTML/MultiFile/Vue 各 1）
- [ ] 补齐到 25 case（HTML 7 / MultiFile 8 / Vue 10）
- [ ] 评测执行器（`com.yupi.yuaicodemother.eval` 包，Phase 0 末尾完成）
- [ ] 跑通基线，写入 `reports/baseline.md`
- [ ] 后续每个 Phase 完成后追加一份 `reports/phase-N.md`
