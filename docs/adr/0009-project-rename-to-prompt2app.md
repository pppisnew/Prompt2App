# ADR-0009：项目重命名为 Prompt2App（含 Java 包路径迁移）

- **状态**：Accepted
- **日期**：2026-06-18
- **决策者**：项目作者
- **相关 Phase**：Phase 0（范围越界，本 ADR 即为越界批准）
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

GitHub 仓库已从教学版 `liyupi/yu-ai-code-mother` 切换到独立仓库 `pppisnew/Prompt2App`，但本地代码仍保留教学版的命名空间：

- Maven `<groupId>com.yupi</groupId> / <artifactId>yu-ai-code-mother</artifactId>`
- Java 包根 `com.yupi.yuaicodemother.**`
- 前端 `package.json` `"name": "yu-ai-code-mother-frontend"`
- 主启动类 `YuAiCodeMotherApplication`
- 根 `README.md` 标题"AI 零代码应用生成平台"（教程口吻）

**事实判断**：

- 仓库名与代码名不一致会让招聘官 / 面试官**第一眼就感到割裂**，削弱"独立项目"的叙事。
- 重命名是一次性机械化操作，越早越好——若延后到 Phase 1-7，重命名变更会和模块化重组、Router 重写等业务改动**混在同一批 commit 里**，git history 难以单独回滚或解读。
- 仓库当前 165 个 Java 源文件、3 个 MyBatis mapper.xml、application.yml、pom.xml 含有旧命名共 ~508 处。

---

## 备选方案（Options）

### 方案 A：仅 README.md 重命名

- 改动文件：1
- 优点：风险最低，纯文档改动，完美贴合 Phase 0 纪律
- 缺点：代码仍叫 yu-ai-code-mother，简历叙事不一致；后续每个 Phase 都要再"顺便改一点"
- 长期成本：高（噪声永久存在）

### 方案 B：A + 构建元数据（pom.xml + 前端 package.json）

- 改动文件：3-10
- 优点：构建产物名一致
- 缺点：Java 包路径仍是旧名，最大问题没解决
- 长期成本：中

### 方案 C：A + B + Java 包路径全量重命名（**本决策**）

- 改动文件：165 + 顶层文档 ~20 处
- 优点：一次性彻底解决；后续每个 Phase 都在干净的 `com.prompt2app` 空间下工作
- 缺点：
  - 越过 Phase 0 边界（Phase 0 明确禁止修改 `src/main/java/com/yupi/yuaicodemother/`）
  - 评测 baseline 尚未跑通，无法用 baseline 验证重命名后行为是否一致
  - 单次 commit 体量大（165 文件）
- 长期成本：低

### 方案 D：延后到 Phase 1 与模块化重组一起做

- 改动文件：165 + 模块化重组的上百文件
- 优点：保持 Phase 0 纪律最严
- 缺点：
  - GitHub 上看到的代码仍是教学版命名（短期割裂）
  - 重命名 + 包重组耦合在一个 PR 里，回滚粒度变粗
- 长期成本：中

---

## 决策（Decision）

**选择方案 C**：在 Phase 0 内完成项目重命名，正式记录"越界批准"。

**改动范围**：

| 类别 | 旧 | 新 |
| --- | --- | --- |
| Maven `groupId` | `com.yupi` | `com.prompt2app` |
| Maven `artifactId` | `yu-ai-code-mother` | `prompt2app` |
| Maven `<name>` | `yu-ai-code-mother` | `Prompt2App` |
| Java 包根 | `com.yupi.yuaicodemother.**` | `com.prompt2app.**` |
| 主启动类 | `YuAiCodeMotherApplication` | `Prompt2AppApplication` |
| 前端 package name | `yu-ai-code-mother-frontend` | `prompt2app-frontend` |
| 根 `README.md` 标题 | "AI 零代码应用生成平台" | "Prompt2App —— ..." |
| 项目逆向工程文档 | 保留 yu-ai-code-mother 提法 | 不改（**历史文档**，分析对象是教学版本身） |

**关键约束**：

- **不改** `yu-ai-code-mother-microservice/`：在 Phase 1 整体删除，重命名是浪费
- **不改** `dev/langchain4j/`：在 Phase 2 整体删除
- **不改** `docs/reverse-engineering/项目逆向工程与Vibe-Coding复现指南.md`：它是**关于教学项目本身**的分析文档，"yu-ai-code-mother" 是分析对象的名字，不是当前项目的名字
- **不改本地目录名** `yu-ai-code-mother/`：仓库名 = GitHub 标识，本地 checkout 目录名不影响任何东西

---

## 代价（Consequences）

### 正面

- 仓库名 / 构建产物名 / Java 包名 / 启动类名 全部对齐 `Prompt2App`
- `git history` 中 commit 094... 之后所有 commit 都在干净命名空间里
- 简历可以直接说"`com.prompt2app` 包下..."，无需解释 com.yupi
- ADR-0001（Phase 1 模块化重组）可以专注分包，不被重命名稀释

### 负面

- **越界 Phase 0 纪律**：本 ADR 即为越界的正式批准，但仍构成一个先例——本 ADR 写明"**仅适用于一次性机械重命名**"，不可被后续越界引用
- **165 文件单次 commit**：代码 review 难度高，但因为是机械替换，rebase / revert 都可以靠 sed 重新生成
- **无 baseline 兜底**：评测集执行器尚未完成，无法量化"重命名前后行为是否一致"。**缓解**：仅做包名替换，不动业务逻辑；commit 后人工执行一次 SSE 生成验证可达性
- **若 Phase 1 决定使用其它包根**（例如 `com.pppisnew.prompt2app`），需要再改一次

### 中性

- 微服务子模块 (`yu-ai-code-mother-microservice/yu-ai-code-*`) 在重命名后将无法编译——**不是问题**，反正 Phase 1 删除

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估：

- 项目重命名后再次需要改 groupId 或包根（说明本次决策中包名选择不当）
- Phase 1 模块化重组发现 `com.prompt2app` 不适合作为根（例如想做多模块 Maven 项目时）
- 出现同名冲突（例如组织内部已有 `com.prompt2app` artifact）

---

## 关于"越界批准"的元规则

> 本 ADR 是项目治理体系上线后第一次显式越界。把"越界规则"写在这里以后未来参考：

- 越界**必须**通过 ADR 批准，commit message 中显式标注 `[ADR-XXXX] override-phase-N`
- 越界 ADR **不构成先例**——后续越界仍需独立 ADR 论证
- 越界**仅限**一次性机械化操作（重命名、删除、迁移），**禁止**用于业务逻辑改动
- 越界后必须立刻**回到当前 Phase 的剩余工作**，禁止"既然越界了顺便也做点别的"

---

## 参考资料（References）

- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §3「明确不做」与本决策不冲突
- [`docs/governance/ai-working-rules.md`](../governance/ai-working-rules.md) §7 "冲突时走 ACP"，本 ADR 即为该流程的产物
- [`docs/roadmap/current-phase.md`](../roadmap/current-phase.md) 已同步更新，把重命名加入 Phase 0 临时任务
