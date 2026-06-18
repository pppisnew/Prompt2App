# AGENTS.md

> 本文件遵循 [agents.md](https://agents.md/) 约定，是所有 AI 代码助手（Claude Code / Cursor / Windsurf / Copilot Agent / Codex 等）的统一入口。
> **任何 AI 在本仓库执行任务前，必须按下方"必读顺序"完成阅读。**

---

## 必读顺序（每次对话开始）

1. **[`PROJECT_CHARTER.md`](./PROJECT_CHARTER.md)** —— 项目目标 / 核心范围 / 明确不做的事
2. **[`docs/governance/ai-working-rules.md`](./docs/governance/ai-working-rules.md)** —— 7 条强制工作规则
3. **[`docs/roadmap/current-phase.md`](./docs/roadmap/current-phase.md)** —— 当前 Phase 的目标 / 完成标准 / 禁止事项
4. **任务相关的 ADR** —— 见 [`docs/adr/README.md`](./docs/adr/README.md) 索引

> **如果上述任何文件与用户当前请求冲突**：停止执行，提示冲突，按 Working Rules 第 7 条走"架构变更提案"流程。

---

## 项目一句话

**Prompt2App** —— 一个 AI 网页生成平台的**重构项目**。原项目是教学微服务 demo，本项目把它改造成"展示 AI 工程能力 + 架构思考 + 工程治理"的作品集。

核心叙事：**从微服务 → 模块化单体 + Router/Agent/Eval/ADR 四件套**。详见 [`PROJECT_CHARTER.md`](./PROJECT_CHARTER.md)。

---

## 仓库地图

```
PROJECT_CHARTER.md          ← 项目宪法（最高优先级）
AGENTS.md                   ← 你在这里
README.md                   ← 用户视角介绍

docs/
  governance/               ← AI 协作规范（必读）
  adr/                      ← 架构决策记录（必读相关篇）
  roadmap/                  ← 当前 Phase / 里程碑 / Backlog
  tasks/                    ← 工作留痕（每个任务一篇）
  architecture/             ← 设计文档（按 Phase 填充）
  reverse-engineering/      ← 项目逆向分析（Phase -1 资产）

eval/
  schema/                   ← 评测 case schema
  cases/                    ← 评测用例集
  reports/                  ← 历次评测报告

src/                        ← 后端代码（Spring Boot）
yu-ai-code-mother-frontend/ ← 前端代码（Vue 3）
yu-ai-code-mother-microservice/  ← 待删除（在 Phase 1 收敛）
```

---

## 常用命令

```bash
# 跑评测集（Phase 5 后可用）
mvn -pl . eval:run

# 验证 ADR 索引完整性
ls docs/adr/*.md

# 查看当前 Phase
cat docs/roadmap/current-phase.md
```

---

## 常见误区（AI 容易犯）

| ❌ 不要这样 | ✅ 应该这样 |
| --- | --- |
| 看到 `yu-ai-code-mother-microservice/` 就去修微服务代码 | 它即将在 Phase 1 删除，仅作为重构对照保留 |
| 看到 `dev/langchain4j/` 就去维护 patch | 它将在 Phase 2 整体删除 |
| 看到要做的事就直接写代码 | 先确认在当前 Phase 范围内，再确认是否需要 ADR / ACP |
| 用户说"加个 X 功能"就加 | 先检查 Charter §3 是否在"明确不做"清单 |
| 完成任务直接说"完成了" | 必须按 DoD 检查，并写 Task Record |

---

## 联系点

- **项目所有者**：本仓库的人类作者（最终决策权）
- **决策仲裁**：按优先级 Charter > ADR > Roadmap > 代码注释
- **冲突处理**：永远停下来问，不擅自合并
