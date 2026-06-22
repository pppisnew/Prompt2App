# Task: VUE 评分 mergedOutput 改读源码 + 文件清单（方案 D）

- **日期**：2026-06-22
- **Phase**：Eval 增量
- **责任人**：项目作者 + AI
- **状态**：Done（2026-06-22 离线验证 6/10 VUE case Rubric 转满分）
- **关联 ADR**：无（bug 修复，非架构变更）
- **前置 Task**：[`2026-06-22-eval-multi-round-determinism.md`](./2026-06-22-eval-multi-round-determinism.md)（3 轮评测揭示 VUE 10/10 全 0）

---

## 1. 目标

修 `DirectServiceInvoker.readMergedOutput` 的 VUE_PROJECT 分支，让 RubricScorer 能在 mergedOutput 中命中源码符号（`ref` / `localStorage` / `addEventListener`）和文件名字面量（`package.json`），修复 VUE 维度 10/10 case 全 0 分问题。

预期：VUE 维度从 0.00 → 60-80（取决于源码实际是否包含 must_contain 关键词）。

## 2. 背景

P0-1（ADR-0013 多轮评测）跑完发现 VUE_PROJECT 3 轮均分 **0.00 ± 0.00**——10/10 case 全 0，标准差为 0。这是**确定性 bug**，非 LLM 抽样噪声。

根因实证（[P0-1 Task Record §附 Round 1 诊断](./2026-06-22-eval-multi-round-determinism.md#附round-1-实证数据--p0-2-重新定位2026-06-22-1314-评测中诊断)）：

| must_contain 关键词 | 源码 + package.json | dist/ | mergedOutput（dist 拼接）|
| --- | --- | --- | --- |
| `package.json` | 0 文件含字面量 | 0 | ❌ miss |
| `vue` | 9 文件 | 1 | ✅ |
| `ref` | 3 | 3 | ✅ |
| `localStorage` | 1 | 1 | ✅ |
| `addEventListener` | 0 | 1 | ✅ |

`must_contain` 要全部命中才 OK——5 个里 1 个 miss 就 veto。`package.json` 这类**文件名字面量**永远不会出现在 dist 编译后 JS 文本里。

**当前 `readMergedOutput` 的 VUE 分支**（`DirectServiceInvoker.java:148-158`）：

```java
if (genType == CodeGenTypeEnum.VUE_PROJECT) {
    File distDir = new File(dir, "dist");
    if (distDir.exists()) {
        readDirRecursive(distDir, sb);   // ← 只读 dist，源码 + 文件名全丢
    } else {
        readDirRecursive(dir, sb);
    }
}
```

build 成功时只读 dist → minified JS → Rubric 必 veto。

## 3. 影响范围（Scope）

**改动文件**：

| 文件 | 改动 |
| --- | --- |
| `src/main/java/com/prompt2app/eval/DirectServiceInvoker.java` | `readMergedOutput` VUE 分支改读源码 + 文件清单 |
| `src/test/java/com/prompt2app/eval/DirectServiceInvokerMergedOutputTest.java`（新建） | VUE + 有 dist / 无 dist / 跳过 node_modules 三场景 |

**明确不做**（防 scope 蔓延）：

- ❌ 不动 HTML / MULTI_FILE 分支（它们 readDirRecursive 行为正确）
- ❌ 不动 `countFiles`（它读 dist 计数，与 RenderScorer.buildSuccess 配合正确）
- ❌ 不动 `RenderScorer.checkVueProject`（已用 buildSuccess 字段，不依赖 mergedOutput）
- ❌ 不动 25 case yaml（已稳定）
- ❌ 不动 `RubricScorer`（它在源码符号齐全后自然能命中）
- ❌ 不启用 LLM-Judge（P1-1 独立 task）
- ❌ 不重跑全 25 case × 3 轮评测（P0-2 验证用已有产物离线测试，不耗 LLM token）

## 4. 修改内容

### 4.1 方案 D 实施

`readMergedOutput` VUE 分支改为：

```java
if (genType == CodeGenTypeEnum.VUE_PROJECT) {
    // 方案 D：读源码 + 文件路径清单，不再读 dist
    // - 让 RubricScorer 能命中源码符号（ref / localStorage / addEventListener）
    // - 让文件名字面量（package.json / App.vue）可命中
    // - 跳过 node_modules（巨大、minified、无评分价值）和 dist（minified、本次 bug 源头）
    sb.append("=== project file tree ===\n");
    appendFileTree(dir, dir, sb);
    sb.append("\n=== source contents ===\n");
    appendSourceContents(dir, dir, sb);
}
```

### 4.2 新增私有方法

```java
/** 列文件相对路径清单（跳过 node_modules / dist / .git），让文件名字面量可被 Rubric 命中。 */
private void appendFileTree(File root, File dir, StringBuilder sb) {
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File f : files) {
        if (isExcluded(f.getName())) continue;
        if (f.isDirectory()) {
            appendFileTree(root, f, sb);
        } else {
            String rel = root.toPath().relativize(f.toPath()).toString();
            sb.append("=== file: ").append(rel).append(" ===\n");
        }
    }
}

/** 读源码文件内容（跳过 node_modules / dist / .git）。 */
private void appendSourceContents(File root, File dir, StringBuilder sb) {
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File f : files) {
        if (isExcluded(f.getName())) continue;
        if (f.isDirectory()) {
            appendSourceContents(root, f, sb);
        } else {
            String rel = root.toPath().relativize(f.toPath()).toString();
            sb.append("--- ").append(rel).append(" ---\n");
            try {
                sb.append(FileUtil.readString(f, StandardCharsets.UTF_8)).append("\n");
            } catch (Exception ignored) {
                sb.append("(binary or unreadable)\n");
            }
        }
    }
}

/** VUE mergedOutput 读取时跳过的目录/文件名。 */
private boolean isExcluded(String name) {
    return "node_modules".equals(name) || "dist".equals(name) || ".git".equals(name);
}
```

### 4.3 兼容性

- HTML / MULTI_FILE 分支：**不变**，仍走 `readDirRecursive`
- `countFiles`：**不变**，仍读 dist 计数
- `RenderScorer.checkVueProject`：**不变**，仍用 `buildSuccess` 字段
- LlmJudgeScorer（P1-1 启用后）：会看到源码 + 文件清单，比 minified JS 更准确，**这是好事**

### 4.4 mergedOutput 语义说明

改动后 mergedOutput 对 VUE 的语义：**项目文件树清单 + 源码内容**（排除 node_modules/dist/.git）。
对 HTML/MULTI_FILE 的语义不变：**目录所有文件内容拼接**。

这个语义差异是合理的——VUE 项目有 build 产物（dist），HTML/MULTI_FILE 没有。读 dist 对评分无价值，读源码才有价值。

## 5. 验证

### 5.1 单元测试

- `DirectServiceInvokerMergedOutputTest`：**4/4 通过**
  - VUE + 有 dist：含 `package.json` 字面量 + 源码符号，不含 dist 内容 ✅
  - VUE + 无 dist：含源码 ✅
  - VUE + 有 node_modules：不含 node_modules 内容 ✅
  - HTML：行为不变（回归保护）✅
- eval 包全量：**60/60 通过**（56 旧 + 4 新），无退化

### 5.2 离线验证（用 Round 1 真实产物，零 LLM token）

用 P0-1 跑出的 10 个 `vue_project_110xxxx` 产物，模拟新 `readMergedOutput` + `RubricScorer`：

| Case | must_contain 数 | 命中 | 状态 | miss 关键词 |
| --- | --- | --- | --- | --- |
| 003-todo-app-vue | 5 | 4 | ❌ | `addEventListener`（Vue 用 `@keydown.enter` 模板语法） |
| 017-weather-dashboard-vue | 7 | 5 | ❌ | `摄氏`, `华氏`（LLM 可能用 `°C`/`°F` 符号） |
| **018-markdown-editor-vue** | 6 | 6 | ✅ | — |
| **019-pomodoro-timer-vue** | 7 | 7 | ✅ | — |
| **020-expense-tracker-vue** | 7 | 7 | ✅ | — |
| **021-kanban-board-vue** | 7 | 7 | ✅ | — |
| **022-recipe-search-vue** | 5 | 5 | ✅ | — |
| 023-quiz-app-vue | 6 | 5 | ❌ | `解析`（LLM 可能用"答案"/"结果"） |
| **024-url-shortener-vue** | 6 | 6 | ✅ | — |
| 025-tic-tac-toe-vue | 5 | 4 | ❌ | `X 胜利`（LLM 可能用"玩家 X 获胜"） |

### 5.3 修复效果

| 指标 | 修复前 | 修复后 | Δ |
| --- | --- | --- | --- |
| VUE Rubric 通过率 | 0/10 | **6/10** | +6 |
| VUE 维度预期均分 | 0.00 | **~60-70**（6 满分 + 4 仍 0） | +60-70 |
| `package.json` 命中率 | 0/10 | **10/10** | +10 |

### 5.4 剩余 4 个 miss 的归因（非方案 D 问题）

| Case | miss | 归因 | 建议 |
| --- | --- | --- | --- |
| 003 | `addEventListener` | Vue 惯用 `@event` 模板语法，编译后才变 `addEventListener` | rubric 改为 `@keydown` 或 `@click` |
| 017 | `摄氏`/`华氏` | LLM 可能用 `°C`/`°F` 符号 | rubric 改为 `°C` 或加"或"语义 |
| 023 | `解析` | LLM 用词随机 | rubric 放宽为 `解析` 或 `答案` |
| 025 | `X 胜利` | LLM 可能用"获胜"/"wins" | rubric 改为 `X` 或 `胜利` |

**这些是 case yaml rubric 设计问题，独立 backlog，不夹带在本 task。**

### 5.5 Charter §4 红线判定

| 项 | 判定 |
| --- | --- |
| 评测回归 | ✅ 无回归（VUE 从 0→6/10 通过，HTML/MULTI 不变） |
| 单测全绿 | ✅ 60/60 |
| 文档先于代码 | ✅ Task Record 在代码前 |
| 不夹带修 | ✅（只改 readMergedOutput VUE 分支，4 个 rubric 调优留独立 backlog） |

## 6. 风险与遗留

- **已知风险**：
  - 源码符号仍可能 miss（如 LLM 没用 `ref` 而用 `reactive`）——这是 case yaml 设计问题，非本 task scope
  - `package.json` 文件内容里可能不含字面量 `"package.json"`，但文件路径清单里有 `=== file: package.json ===`，可命中
- **遗留事项**：
  - 若离线验证发现某些 case 仍 miss，需排查具体 case yaml 的 must_contain 是否合理（独立 backlog）
  - LLM-Judge 启用后（P1-1）要确认 Judge 对"源码 + 文件清单"的评分合理

## 7. 对治理体系的更新

- [x] 本 Task Record
- [ ] 完成后更新 `docs/tasks/README.md` 索引
- [ ] 完成后更新 `docs/roadmap/current-phase.md` backlog（P0-2 标 ✅）

## 8. 实施顺序

1. 改 `readMergedOutput` VUE 分支 + 新增 3 个私有方法
2. 新建 `DirectServiceInvokerMergedOutputTest` + 4 个用例
3. 编译 + eval 包全量单测
4. 离线验证：用 `vue_project_1100002` 产物模拟评分
5. 回填本 Task Record §5 验证结果
