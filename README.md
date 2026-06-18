# Prompt2App

> **AI 网页生成平台 · 个人作品集项目**
>
> 用户输入需求描述，AI 自动选择策略生成可运行网页，支持流式输出、可视化编辑、一键部署。

---

## 项目状态

🚧 **重构中**（Phase 0 · 评测基线，2026-06）

本项目源自[程序员鱼皮的教学项目 yu-ai-code-mother](https://github.com/liyupi/yu-ai-code-mother)，
目前正在进行个人化重构，目标是把它从"教学微服务 demo"改造成"展示 AI 工程能力 + 架构思考 + 工程治理"的作品集项目。

完整的项目目标、范围与决策见 [`PROJECT_CHARTER.md`](./PROJECT_CHARTER.md)。

---

## 核心能力（重构目标）

| # | 能力 | 当前状态 |
| --- | --- | --- |
| 1 | **AI Router** —— 规则 + LLM 兜底两层路由（HTML / MultiFile / Vue 三策略） | Phase 4 计划中 |
| 2 | **Tool Calling Agent** —— 含三层安全防御 | Phase 3 计划中 |
| 3 | **Prompt Eval 体系** —— 25 case × 三维评分 + CI 回归 | Phase 0 进行中 |
| 4 | **ADR 决策记录** —— 每个架构选择都有可追溯的论证 | 持续输出 |
| 5 | **Generation Metrics** —— 12 维埋点 + SQL 报表 | Phase 6 计划中 |

---

## 仓库导览

```
PROJECT_CHARTER.md          ← 项目宪法（目标 / 范围 / 明确不做的事）
AGENTS.md                   ← AI 助手协作入口
README.md                   ← 你在这里

docs/
  governance/               ← 协作规则、ACP 模板、DoD
  adr/                      ← 架构决策记录（ADR-0008 / ADR-0009 已就绪）
  roadmap/                  ← 当前 Phase / 22 天里程碑 / Backlog
  tasks/                    ← 工作留痕（每个任务一篇）
  architecture/             ← 设计文档（按 Phase 填充）
  reverse-engineering/      ← 教学版项目逆向分析（Phase -1 资产）

eval/
  schema/                   ← 评测 case schema
  cases/                    ← 评测用例集（3/25）
  reports/                  ← 评测报告（待跑 baseline）

src/                        ← Spring Boot 3 后端（com.prompt2app.**）
yu-ai-code-mother-frontend/ ← Vue 3 前端（待 Phase 1 重命名为 prompt2app-frontend）
yu-ai-code-mother-microservice/  ← 教学版微服务（待 Phase 1 删除）
```

---

## 技术栈

- **后端**：Spring Boot 3 / LangChain4j / MyBatis-Flex / Redis / MySQL
- **前端**：Vue 3 / Vite / Ant Design Vue
- **AI**：DeepSeek（主） / 多模型路由（计划）
- **测试**：JUnit 5 + Playwright（Phase 5 加入）

---

## 快速开始

> ⚠️ 项目重构进行中，构建路径正在变化。**Phase 0 完成（评测体系跑通）后会补全此节**。
>
> 当前可见的"产物"是治理体系本身：[`PROJECT_CHARTER.md`](./PROJECT_CHARTER.md)、[`docs/adr/`](./docs/adr/)、[`eval/`](./eval/)。

---

## 致谢

本项目骨架来自[程序员鱼皮 - yu-ai-code-mother 教学项目](https://github.com/liyupi/yu-ai-code-mother)。
完整代码 + 视频教程是非常优秀的 AI 全栈学习资源。本仓库在此基础上做的是**个人化的工程治理重构**，并非教学版的替代品。

教学版本存档于 git tag [`microservice-final`](https://github.com/pppisnew/Prompt2App/tree/microservice-final)，可对比演化。

---

## 许可证

参见 [`LICENSE`](./LICENSE)（如有）。
