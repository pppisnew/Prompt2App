# Eval Design

> **状态**：Stub（待 Phase 5 完成后撰写）
> **对应 Phase**：Phase 5 · Eval 体系自动化
> **相关 ADR**：ADR-0005（待写）

---

## 设计要点（Phase 5 启动时展开）

### 三维评分

| 维度 | 工具 | 性质 |
| --- | --- | --- |
| 编译 / 构建成功 | `npm run build` / HTML 直接 Playwright load | 否决项 |
| 页面渲染非空 | Playwright `page.locator('body').textContent().length > N` | 否决项 |
| LLM-as-Judge 主观分 | 评判模型 + rubric prompt | 主分 0-100 |

### 执行流程

```
载入 eval/cases/*.yaml
   ↓
逐 case 调 Router → Agent
   ↓
收集产物 → 三维评分
   ↓
对比 baseline.md → diff
   ↓
写 eval/reports/<sha>.md
   ↓
若 diff > 5% → CI 红灯
```

### 待详写

- [ ] 执行器代码组织（`com.yupi.yuaicodemother.eval`）
- [ ] LLM-Judge 的 system prompt 模板
- [ ] 评分稳定性验证方法（同 case 跑 3 次方差 < 10%）
- [ ] CI 集成方案（GitHub Actions / 本地 hook）
- [ ] 报表 Markdown 模板

---

> _此处暂为占位。Phase 5 启动时按 [`README.md`](./README.md) §写作规范 撰写。_
