# Task: 项目治理体系与评测骨架搭建（Phase 0 启动）

- **日期**：2026-06-18
- **Phase**：Phase 0 · 评测基线
- **责任人**：项目作者（指导）+ AI 助手（执行）
- **状态**：Done
- **关联 ADR**：ADR-0008
- **关联 ACP**：无（本次工作均在 Phase 0 范围内）

---

## 1. 目标

完成项目从"教学微服务 demo"到"个人作品集 AI 工程项目"的**治理基础设施搭建**：

1. 切分支 + 打 tag，把旧版本作为对照基线封存
2. 建立 ADR / Task / Roadmap / Governance 四套治理目录
3. 写下 PROJECT_CHARTER.md 作为项目宪法
4. 写下 AGENTS.md / AI Working Rules 作为 AI 协作约束
5. 启动 Phase 0：评测集骨架、3 条示范 case、ADR-0008

---

## 2. 背景

经过多轮迭代式讨论（共约 6 轮），项目目标从原始模糊的"重构成 SaaS"明确收敛为"作品集项目，展示 AI 工程能力"。

讨论过程中确立的核心原则：

- 不做微服务、Workflow、OSS、JWT、React 迁移、Docker 沙箱等 SaaS 化建设
- 重点投入 4+1 个能力：Eval / Router / Agent+Safety / ADR / Metrics
- 引入治理体系防止"AI 把项目越改越乱"——这是作品集项目的真实死法

详见 `PROJECT_CHARTER.md` §1-§3。

---

## 3. 影响范围（Scope）

- **代码模块**：无（本次任务不动业务代码，仅治理与文档）
- **文档**：新增 `PROJECT_CHARTER.md`、`AGENTS.md`、`docs/`（adr / governance / roadmap / tasks / architecture / reverse-engineering 共 6 子目录）
- **数据**：新增 `eval/`（schema / cases / reports）
- **配置**：无（不动 `pom.xml`）
- **Git**：新增 tag `microservice-final`、新增分支 `feature/ai-engineering-rebuild`、远程 `origin` 切换为 `https://github.com/pppisnew/Prompt2App.git`

---

## 4. 修改内容

### 4.1 Git 操作

- 打 tag：`microservice-final` → 旧版微服务架构终态（commit 893918c）
- 新建分支：`feature/ai-engineering-rebuild`，所有重构在此分支进行
- 远程切换：`origin` 由 `liyupi/yu-ai-code-mother` 切到 `pppisnew/Prompt2App`
- **未提交、未推送**——等用户确认后再做

### 4.2 新增文件（共 17 个）

**根级**
- `PROJECT_CHARTER.md` —— 项目宪法
- `AGENTS.md` —— AI 入口指针

**docs/governance/**
- `README.md`
- `ai-working-rules.md` —— 7 条强制规则
- `architecture-change-proposal-template.md` —— ACP 模板
- `definition-of-done.md` —— 8 个 Phase 的 DoD

**docs/roadmap/**
- `README.md`
- `current-phase.md` —— Phase 0 当前状态
- `milestones.md` —— 22 天 8 Phase 总览
- `backlog.md` —— 延后事项

**docs/tasks/**
- `README.md`
- `_template.md` —— Task Record 模板
- `2026-06-18-phase0-bootstrap.md` —— 本文件

**docs/adr/**
- `README.md`
- `_template.md` —— ADR 模板
- `0008-evaluation-first.md` —— 第一篇 ADR 示范

**eval/**
- `README.md`
- `schema/case.schema.yaml` —— case 字段定义
- `cases/001-personal-resume-page.yaml` (HTML)
- `cases/002-coffee-shop-multipage.yaml` (MultiFile)
- `cases/003-todo-app-vue.yaml` (Vue)

### 4.3 移动文件

- `doc/项目逆向工程与Vibe-Coding复现指南.md` → `docs/reverse-engineering/项目逆向工程与Vibe-Coding复现指南.md`（git mv）
- 旧 `doc/` 目录已删除

### 4.4 待建（low priority，本任务不做）

- `docs/architecture/` 5 个 stub 文件
- ADR-0001 ~ ADR-0007 占位文件

---

## 5. 验证

### 通用 DoD

- [x] 该 Phase 的 ADR 已写并 Accepted（ADR-0008 已 Accepted）
- [x] 该 Phase 至少 1 篇 Task Record 已留存（本文件即是）
- [x] `current-phase.md` 的 checklist 已更新（其中 schema、3 case、ADR-0008 已勾选）
- [x] `milestones.md` 中 Phase 0 状态为 In Progress
- [x] 本次工作不会进入 commit message（Phase tag 由后续 commit 携带）
- [x] 没有遗留 `// TODO` 在主路径代码（本次未碰主路径代码）

### 任务专属验证

- [x] `git status` 显示新增的 docs/ 与 eval/ 全部 untracked，**没有任何 src/ 下的改动**
- [x] `git tag` 显示 `microservice-final`
- [x] `git branch --show-current` 显示 `feature/ai-engineering-rebuild`
- [x] `git remote -v` 显示新仓库地址
- [x] 所有新增文件能被 IDE 正常识别（YAML schema 引用合法、Markdown 链接闭合）

---

## 6. 风险与遗留

### 已知风险

- **未 commit 未推**：当前所有改动在工作树。若机器异常，可能丢失。建议在后续操作前 commit。
- **`doc/` 删除**：`git mv` 行为依赖 git 是否能识别为 rename。已用 `mv` 兜底，但 commit 时会显示 D + A 两条记录而非 R。无功能影响。
- **微服务版本仍在工作树**：`yu-ai-code-mother-microservice/` 与 `dev/langchain4j/` 仍存在。按计划在 Phase 1/2 处理。

### 遗留事项（已记录到 backlog.md）

- Phase 7 改 README.md 为面向招聘官版本
- Phase 7 处理 `grafana/` 与 `prometheus.yml`
- ADR-0007 中需要写明"曾评估过 Plan-Execute-Review"

---

## 7. 对治理体系的更新

- [x] 更新了 `docs/roadmap/current-phase.md` 的 checklist？✅
- [x] 更新了 `docs/roadmap/milestones.md` 的状态？✅（Phase 0 → In Progress）
- [x] 创建/更新了 ADR？✅ ADR-0008
- [x] 添加了新条目到 `backlog.md`？✅

---

## 8. 下一步建议

按治理体系的"DoD + 留痕"要求，下一步顺序：

1. **commit 当前进度**（用户确认）：
   ```
   chore(phase-0): bootstrap project governance and eval framework
   ```
   建议拆成 2 commit：governance 一坨、eval 一坨；或合并成一个 atomic commit，二选一。

2. **推送到 `pppisnew/Prompt2App`**（用户确认）：
   - 推 master + tag microservice-final（保留演化叙事）
   - 推 feature/ai-engineering-rebuild

3. **继续 Phase 0 收尾**：补齐 22 条 case；写评测执行器；跑 baseline。

4. **不要做的事**：
   - 不要现在动 microservice 删除（Phase 1）
   - 不要现在动 langchain4j patch（Phase 2）
   - 不要现在动 Router / Agent（Phase 3-4）
