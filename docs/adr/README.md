# Architecture Decision Records

本目录记录本项目重构过程中所有**重要的架构决策**。每条 ADR 是一份**独立的、不可变的**文档：写完之后不删不改，状态变化通过新增 ADR 来覆盖。

## 为什么写 ADR

- **可追溯**：6 个月后回看，能立刻回忆"当时为什么选 A 不选 B"。
- **可面试**：架构决策的过程比代码本身更能体现工程能力。
- **可演化**：未来需求变化时，能基于"原始约束"判断是否要推翻决策。

## 规范

- 文件名：`NNNN-kebab-case-title.md`，编号四位、单调递增。
- 每条 ADR 必须包含：**背景 / 备选方案（≥2）/ 决策 / 代价 / 复盘指标**。
- 状态字段只允许：`Proposed` / `Accepted` / `Deprecated` / `Superseded by ADR-XXXX`。
- 模板见 [`_template.md`](./_template.md)。

## 索引

| # | 标题 | 状态 | 相关 Phase |
| --- | --- | --- | --- |
| [0008](./0008-evaluation-first.md) | 重构开始之前先建立评测集 | Accepted | Phase 0 |
| [0009](./0009-project-rename-to-prompt2app.md) | 项目重命名为 Prompt2App（含 Java 包路径迁移） | Accepted | Phase 0（越界批准） |
| [0001](./0001-modular-monolith.md) | 从微服务回退到模块化单体 | Accepted | Phase 1 |
| [0002](./0002-remove-langchain4j-patch.md) | 升级 LangChain4j 1.1.0→1.5.1 + 删除源码覆盖 patch | Accepted | Phase 2 |
| [0004](./0004-tool-safety.md) | Tool Calling 三层安全防御体系 | Accepted | Phase 3 |
| [0003](./0003-ai-router-two-layer.md) | AI Router 采用规则 + LLM 兜底两层架构 | Accepted | Phase 4 |
| [0005](./0005-evaluation-automation.md) | 评测体系自动化（三维评分 + diff + CI） | Accepted | Phase 5 |
| [0006](./0006-no-minio.md) | 当前阶段不引入 MinIO/OSS 对象存储 | Accepted | Phase 7 |
| [0007](./0007-no-langgraph4j-workflow.md) | 不引入 LangGraph4j Workflow 作为主路径 | Accepted | Phase 7 |
| [0010](./0010-config-unification.md) | 配置统一化（.env + Spring profiles + Prompt2AppProperties） | Accepted | Phase 8（v1.1 触发） |
| [0011](./0011-drop-redis-chat-memory-store.md) | 删除 RedisChatMemoryStore：未生效的依赖应及时回收 | Accepted | Phase 8（增量 / 启动事故链 #3） |
