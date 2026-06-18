# Task: <一句话标题>

- **日期**：YYYY-MM-DD
- **Phase**：Phase N
- **责任人**：xxx（人 / AI / 协作）
- **状态**：In Progress | Done | Reverted | Abandoned
- **关联 ADR**：ADR-xxxx（如有）
- **关联 ACP**：ACP-YYYYMMDD-xxx（如有）

---

## 1. 目标

> 这次任务想达成什么？1-2 句话讲清楚。

## 2. 背景

> 为什么现在做？触发自哪个对话 / 哪条 backlog？

## 3. 影响范围（Scope）

明确列出本次任务**允许触碰**的范围。超出此范围的任何修改都必须先停下来重新规划。

- **代码模块**：例 `eval/` / `src/main/java/.../router/`
- **文档**：例 `docs/adr/` / `docs/roadmap/current-phase.md`
- **数据**：例 `eval/cases/`
- **配置**：例 `pom.xml`（如新增依赖请说明并确认在 Phase 范围内）

## 4. 修改内容

> 真正改了什么。可以是 commit list、文件变更摘要、或要点 bullets。

- 新增：…
- 修改：…
- 删除：…
- 移动 / 重命名：…

## 5. 验证

> 怎么证明这次任务是"做完了"而不是"做了"。

- [ ] 通用 DoD（见 `docs/governance/definition-of-done.md` §通用 DoD）
- [ ] 本任务专属验证：…

## 6. 风险与遗留

- **已知风险**：…
- **遗留事项**：写到 [`docs/roadmap/backlog.md`](../roadmap/backlog.md) 的项目，列出来：
  - …

## 7. 对治理体系的更新

- [ ] 更新了 `docs/roadmap/current-phase.md` 的 checklist？
- [ ] 更新了 `docs/roadmap/milestones.md` 的状态？
- [ ] 创建/更新了 ADR？哪一篇？
- [ ] 添加了新条目到 `backlog.md`？

## 8. 下一步建议

> 给后续协作者（包括明天的自己）的建议。
