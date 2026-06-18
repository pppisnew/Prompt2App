# Definition of Done (DoD) per Phase

> 每个 Phase 必须满足全部 DoD 才允许标记为完成。**部分完成 = IN_PROGRESS**，不允许"差不多就行"。

---

## 通用 DoD（所有 Phase 共有）

无论哪个 Phase，下列项必须全部满足：

- [ ] 该 Phase 的 ADR 已写并 Accepted
- [ ] 该 Phase 至少 1 篇 Task Record 已留存
- [ ] [`docs/roadmap/current-phase.md`](../roadmap/current-phase.md) 的 checklist 全部勾选
- [ ] [`docs/roadmap/milestones.md`](../roadmap/milestones.md) 中该 Phase 状态更新为 `Done`
- [ ] commit message 中标注 Phase（例：`feat(phase-3): tool safety circuit breaker`）
- [ ] 没有遗留 `// TODO` 在主路径代码（评测 case 内的 TODO 不算）

---

## Phase 0 · 评测基线

- [ ] `eval/schema/case.schema.yaml` 完成
- [ ] `eval/cases/` 共 ≥ 25 条 case（HTML 7 / MultiFile 8 / Vue 10）
- [ ] 每条 case 的 `must_contain` / `must_not_contain` / `llm_judge_dimensions` 完整
- [ ] 评测执行器（`com.yupi.yuaicodemother.eval.*`）跑通端到端
- [ ] `eval/reports/baseline.md` 已生成（基于现版项目）
- [ ] ADR-0008 Accepted

---

## Phase 1 · 模块化单体收敛

- [ ] `yu-ai-code-mother-microservice/` 删除（git mv 到 `legacy/` 或直接删，由 ADR-0001 决定）
- [ ] 主线 `src/main/java/com/yupi/yuaicodemother` 包按领域分模块完成（`app / router / agent / eval / metric / infra`）
- [ ] 启动测试通过：`mvn spring-boot:run` 应用能起、健康检查 OK
- [ ] 评测集回归：分数与 baseline 对比 **不低于 -5%**
- [ ] ADR-0001 Accepted

---

## Phase 2 · 删除 LangChain4j 源码覆盖

- [ ] `src/main/java/dev/langchain4j/` 整体删除
- [ ] LangChain4j 依赖升级到稳定版（pom.xml diff 在 ADR 中说明）
- [ ] 流式工具事件功能验证（手工 + 评测集）
- [ ] 评测集回归 **不低于 -5%**
- [ ] ADR-0002 Accepted

---

## Phase 3 · Tool 安全体系

- [ ] 三层防御实施：Schema 校验 / 工作目录绑定 / 调用次数熔断
- [ ] **20+ 条单元测试**全部通过，覆盖至少：
  - 路径越权（`..` / 绝对路径 / 软链接）
  - 关键文件保护（`package.json` 等不能被删）
  - 调用次数熔断（超过阈值自动中断）
  - 同一文件高频修改告警
- [ ] CI 中包含上述测试，失败即阻断合入
- [ ] ADR-0004 Accepted

---

## Phase 4 · AI Router 重写

- [ ] 规则路由层实现（关键词 + 字数 + 历史成功率）
- [ ] LLM 兜底层实现（小模型 + few-shot）
- [ ] 路由决策埋点入库（`router_decision` 表或同表的字段）
- [ ] 评测集回归 **不低于 baseline**（路由策略改进应当持平或提升）
- [ ] 路由准确率指标输出（与 case 的 `expected_strategy` 对比）
- [ ] ADR-0003 Accepted

---

## Phase 5 · 评测体系自动化

- [ ] 评测执行器升级：支持批量跑 + 三维评分
- [ ] LLM-as-Judge 实现且评分稳定性验证（同一 case 跑 3 次方差 < 10%）
- [ ] Playwright 渲染检查（仅 `body.textContent.length > N`）
- [ ] CI 集成：每次 PR 自动跑评测，分数下降 > 5% 红灯
- [ ] Markdown 报表自动生成到 `eval/reports/<commit-sha>.md`
- [ ] ADR-0005 Accepted

---

## Phase 6 · 生成质量埋点

- [ ] `generation_metric` 表创建，包含 12 维字段（见 [`architecture/metric-design.md`](../architecture/metric-design.md)）
- [ ] 每次生成请求落表
- [ ] 一页 SQL 报表（成功率 / 平均耗时 / 平均成本 / 路由偏差率）
- [ ] 不引入 Grafana（按 Charter §3）
- [ ] ADR-0006 Accepted（如涉及 OSS 决策）

---

## Phase 7 · 收尾

- [ ] ADR-0007（不引入 Workflow）写完
- [ ] ADR-0006（不上 MinIO）写完
- [ ] README.md 更新为面向用户/招聘官的版本
- [ ] 简历段落定稿，与 README 一致
- [ ] 所有未完成 ADR 编号补齐或显式删除
- [ ] 一份"项目演示视频/截图"放在 README

---

## 如何标记 Phase 完成

```
1. 自检：本文件该 Phase 全部 [x]
2. 跑评测集，确认未退化
3. 更新 docs/roadmap/current-phase.md 切换到下一 Phase
4. 在 docs/roadmap/milestones.md 把当前 Phase 标 Done
5. commit message: "chore(phase-N): mark phase N as done"
```
