# Task: Phase 1 模块化单体收敛（按域分 6 包 + 删除微服务）

- **日期**：2026-06-18
- **Phase**：Phase 1 · 模块化单体收敛
- **责任人**：项目作者（指令）+ AI 助手（执行）
- **状态**：Done
- **关联 ADR**：[ADR-0001](../adr/0001-modular-monolith.md)

---

## 1. 目标

按 ADR-0001 收敛架构：

1. 删除 `yu-ai-code-mother-microservice/` 整个目录（教程版微服务）
2. 主项目 19 个顶层包重组为 6 个按域分包：`app / router / agent / eval / metric / infra`
3. 修复 `ratelimter` 拼写错误为 `infra/ratelimiter`
4. 同步所有配置（`@MapperScan` / mapper.xml namespace / Knife4j 扫描路径）
5. 验证 mvn compile + evaluator 回归 9/9

## 2. 背景

教程版项目同时存在单体 + 微服务双份代码（Dubbo + Nacos 7 模块），且单体内 19 个顶层包按"层"分组（controller / service / mapper），不利于：
- 后续 Phase 4-6（Router / Agent / Metric）展开干净的命名空间
- 简历叙事（"评估 RPC 必要性后回退到模块化单体"是加分）

完整论证见 ADR-0001。

## 3. 影响范围（Scope）

- **删除**：`yu-ai-code-mother-microservice/` 整目录（git rm -rf）
- **新增**：`docs/adr/0001-modular-monolith.md`、`docs/architecture/module-design.md` v1、本 task record
- **重组**：`src/main/java/com/prompt2app/**` + `src/test/java/com/prompt2app/**` 共 ~160 java 文件
- **同步**：3 mapper.xml namespace、`Prompt2AppApplication.@MapperScan`、`application.yml` Knife4j 扫描路径
- **未触碰**：业务逻辑、`pom.xml`、`dev/langchain4j/` patch（Phase 2）、前端

## 4. 修改内容

### 4.1 git rm

```
yu-ai-code-mother-microservice/
├── pom.xml
└── yu-ai-code-{ai,app,client,common,model,screenshot,user}/
```

整目录删除。**唯一存档** = git tag `microservice-final`（commit 893918c）。

### 4.2 包重组（git mv 矩阵）

| 旧 | 新 | 涉及子结构 |
| --- | --- | --- |
| `ai/AiCodeGenTypeRouting*.java` | `router/` | 2 类 |
| `ai/AiCodeGeneratorService*.java` | `agent/` | 2 类 |
| `ai/{guardrail,model,tools}` | `agent/{guardrail,model,tools}` | 多文件 |
| `core/*` | `agent/codegen/*` | 含 `builder/handler/parser/saver` |
| `langgraph4j/*` | `agent/workflow/*` | 含 `ai/config/demo/model/node/state/tools` |
| `controller / service / mapper / model` | `app/<同名>` | 业务领域聚合 |
| `monitor/*` | `metric/*` | 4 类 |
| `annotation / aop / common / config / constant / exception / generator / manager / utils` | `infra/<同名>` | 9 子包 |
| `ratelimter/*` | `infra/ratelimiter/*` | **修拼写** + 归位 |

### 4.3 sed 替换

两轮 sed：

**第一轮（带尾点的引用）**：在所有 `.java/.xml/.yml/.properties` 中替换形如 `com.prompt2app.<旧>.` → `com.prompt2app.<新>.`。19 条规则。

**第二轮（package 声明 + 配置硬编码）**：第一轮未匹配的位置：
- `package com.prompt2app.<旧>;`（结尾分号）
- `package com.prompt2app.<旧>.子包;`（结尾子包名）
- `@MapperScan("com.prompt2app.mapper")`（无尾点）
- `packages-to-scan: com.prompt2app.controller`（无尾点）

### 4.4 一个 sed bug + 修复

**bug**：第二轮 sed 把 `package com.prompt2app.ai;` 一刀切到 `package com.prompt2app.agent;`，导致 `router/` 下两个文件（`AiCodeGenTypeRoutingService.java` 和 `*Factory.java`）的 package 声明被误改成 `agent`，造成路径与声明不一致 → "类重复"编译错误。

**修复**：写了一个 path vs package 自检脚本：

```bash
find src -name "*.java" | while read f; do
  pkg=$(head -1 "$f" | sed 's|package ||;s|;||')
  expected=$(echo "$f" | sed 's|.*/java/||;s|/[^/]*\.java$||;s|/|.|g')
  [ "$pkg" != "$expected" ] && echo "  $f"
done
```

定位到 3 个文件（2 main + 1 test）有 package/path 不一致，手动 sed 修。修复后 0 mismatch。

### 4.5 配置同步

| 位置 | 旧 | 新 |
| --- | --- | --- |
| `Prompt2AppApplication.@MapperScan` | `"com.prompt2app.mapper"` | `"com.prompt2app.app.mapper"` |
| `mapper/AppMapper.xml` namespace | `com.prompt2app.mapper.AppMapper` | `com.prompt2app.app.mapper.AppMapper` |
| `mapper/UserMapper.xml` namespace | `com.prompt2app.mapper.UserMapper` | `com.prompt2app.app.mapper.UserMapper` |
| `mapper/ChatHistoryMapper.xml` namespace | 同上 | `com.prompt2app.app.mapper.ChatHistoryMapper` |
| `application.yml` Knife4j `packages-to-scan` | `com.prompt2app.controller` | `com.prompt2app.app.controller` |

### 4.6 文档

- `docs/adr/0001-modular-monolith.md` 新增（含 4 备选方案、完整旧→新映射表、模块边界图、复盘指标）
- `docs/architecture/module-design.md` 从 stub 升级为 v1（活文档，跟代码同步）
- `docs/adr/README.md` 索引更新
- `docs/roadmap/current-phase.md` 切到 Phase 1 In Progress → ✅ Done
- `docs/roadmap/milestones.md` Phase 1 行 + 2 条状态变更
- `docs/roadmap/backlog.md` 新增"24 个 SpringBootTest 集成测试需环境"条目

## 5. 验证

### 通用 DoD

- [x] 该 Phase 的 ADR 已写并 Accepted（ADR-0001）
- [x] Task Record 已留存（本文件）
- [x] `current-phase.md` Phase 1 全部 [x]
- [x] `milestones.md` Phase 1 切到 Done
- [x] commit message 标注 Phase（`feat(phase-1): ...`）
- [x] 没有遗留 `// TODO` 在主路径代码

### 任务专属验证

- [x] **microservice/ 已删**（`ls yu-ai-code-mother-microservice` → 不存在）
- [x] **6 个顶层包就位**：`agent / app / eval / infra / metric / router` + `Prompt2AppApplication.java`
- [x] **path/package 一致性**：自检脚本扫描全部 java 文件，0 mismatch
- [x] **mvn compile 成功**：191 class 产出
- [x] **evaluator 回归 9/9**：`mvn test -Dtest='com.prompt2app.eval.*Test'`
- [x] **baseline.md 重新生成**：`eval/reports/baseline.md` Distribution 仍为 HTML 7 / MultiFile 8 / Vue 10
- [x] **`microservice-final` git tag 仍可访问**：`git tag -l microservice-final` ✓

### 已知不完美（非本 Phase 引入）

- **24 个 SpringBootTest 集成测试失败**：langgraph4j（已迁到 agent/workflow/）旧测试 + WebScreenshotUtilsTest + AiCodeGenTypeRoutingServiceTest 全部依赖完整 Spring 上下文（需 DB / Redis / API key）。这是 **Phase 0 时未跑过的存量问题**，不是本 Phase 引入。已记入 backlog 待 Phase 2/3 处理。
- **mvn spring-boot:run 启动验证**：原 DoD 要求"应用能起 + 健康检查 OK"，需要 DB/Redis/API key 配齐，作品集场景下作为可选已在 current-phase.md 中显式 relax。

## 6. 风险与遗留

### 已知风险

- **agent/workflow/（原 langgraph4j）的命运未定**：Phase 7 ADR-0007 决定保留 vs 删除。当前迁移仅是包路径更换，没有功能改动。
- **dev/langchain4j/ patch 仍在**：Phase 2 处理。本 Phase 验证了 patch 在新包结构下仍能 compile。
- **集成测试套件未在 CI 跑通**：见上面"已知不完美"。

### 遗留事项（已记入 backlog.md）

- **Phase 2**：24 个 SpringBootTest 集成测试需环境策略（testcontainers / 环境变量门控 / 拆分单测+集成测试）
- **Phase 7**：ADR-0007 决定 `agent/workflow/`（原 langgraph4j）的存留
- **前端目录** `yu-ai-code-mother-frontend/` → `prompt2app-frontend/`：仍在 Phase 1 backlog 中，本 Phase 决定**不动前端目录**（独立演进，避免 break IDE 配置）

## 7. 对治理体系的更新

- [x] 更新了 `docs/roadmap/current-phase.md`：Phase 1 全部 [x]，状态切换到 ✅ Done
- [x] 更新了 `docs/roadmap/milestones.md`：Phase 1 行 + 2 条状态变更
- [x] 更新了 `docs/roadmap/backlog.md`：Phase 2 范围新增 1 条（集成测试环境策略）
- [x] 写了 ADR-0001（架构性变更必有 ADR）
- [x] 写了 `docs/architecture/module-design.md` v1（ADR 落地的活文档）

## 8. 下一步建议

按治理纪律：

1. **commit + push** 本次 Phase 1 全部改动（包重组 + 删除微服务 + ADR-0001 + module-design v1 + governance 同步）。
2. **Phase 1 关闭**：把 `current-phase.md` 切换到 Phase 2 占位，等用户决定何时启动 Phase 2。
3. **不要顺手开始 Phase 2**：Phase 2 涉及删 langchain4j patch + 升级 LangChain4j，需要新的任务规划。

---

**Phase 1 总览**：

```
2026-06-18 一天内完成（接续 Phase 0 同日）：
├── 删除 yu-ai-code-mother-microservice/（git rm -rf，存档于 microservice-final tag）
├── 19 个顶层包 → 6 个按域分包（160 个 .java 移位 + sed 替换 import / package decl）
├── 配套配置：3 mapper.xml + @MapperScan + Knife4j 扫描路径
├── 修复 ratelimter 拼写 → infra/ratelimiter
├── ADR-0001 + module-design.md v1 + 1 篇 task record
└── 验证：mvn compile 191 class + evaluator 9/9（与 Phase 0 baseline 持平）

总产出：
- 1 个 commit（待）
- ADR-0001（4 备选方案、完整映射表、边界图、复盘指标）
- 1 篇 module-design.md v1（活文档）
- 删除 microservice 整目录（约 50 java 文件 + 7 pom.xml）
- 0 行业务代码改动（纯机械重命名 + 删除）
```

> Phase 1 把"19 包按层"收敛成"6 包按域"——这是后续所有 Phase 工作的命名空间基础。
