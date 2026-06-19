# Tasks

> 工作留痕。**任何持续 > 30 分钟的工作必须在这里留一篇**——哪怕是 AI 自动完成的。

## 命名约定

```
YYYY-MM-DD-<phase>-<slug>.md

例：
  2026-06-18-phase0-bootstrap.md
  2026-06-19-phase0-fill-cases.md
  2026-06-20-phase1-remove-microservice.md
```

## 模板

见 [`_template.md`](./_template.md)。

## 索引

| 日期 | Phase | 任务 | 责任人 | 状态 |
| --- | --- | --- | --- | --- |
| 2026-06-18 | Phase 0 | [项目治理体系与评测骨架搭建](./2026-06-18-phase0-bootstrap.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-18 | Phase 0（越界） | [项目重命名为 Prompt2App](./2026-06-18-phase0-rename-to-prompt2app.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-18 | Phase 0 | [补齐评测集 v0 至 25 条 case](./2026-06-18-phase0-fill-eval-cases.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-18 | Phase 0 | [评测执行器 v0 + baseline 跑通（收官）](./2026-06-18-phase0-evaluator-and-baseline.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-18 | Phase 1 | [模块化单体收敛（按域分 6 包 + 删除微服务）](./2026-06-18-phase1-modular-monolith.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-18 | Phase 2 | [删除 LangChain4j 源码覆盖 patch + 升级到 1.5.1](./2026-06-18-phase2-remove-langchain4j-patch.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-18 | Phase 3 | [Tool Calling 三层安全防御 + 27 单测](./2026-06-18-phase3-tool-safety.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-18 | Phase 4 | [AI Router 规则 + LLM 兜底两层架构](./2026-06-18-phase4-router.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-19 | Phase 5 | [评测体系自动化（三维评分 + Diff + CI）](./2026-06-19-phase5-eval-automation.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-19 | Phase 6 | [生成质量埋点（generation_metric + 报表 API）](./2026-06-19-phase6-metrics.md) | 项目作者 + AI | ✅ 完成 |
| 2026-06-19 | Phase 7 | [项目收尾 + 完成总结（ADR-0006/0007 + README v2）](./2026-06-19-phase7-closeout.md) | 项目作者 + AI | ✅ **项目完成** |
| 2026-06-19 | Phase 8 | [配置统一化（ADR-0010 + spring-dotenv + 7 调用点迁移）](./2026-06-19-phase8-config-unification.md) | 项目作者 + AI | ✅ 完成（v1.0→v1.1）|
| 2026-06-19 | Phase 8（增量） | [启动事故复盘：DB 自动建库 + Redis namespace 隔离](./2026-06-19-phase8-bootstrap-incident-fix.md) | 项目作者 + AI | ✅ 完成（最小修复，未立 ADR）|
