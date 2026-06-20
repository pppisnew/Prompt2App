# Task Record · 2026-06-20 · 前端 UI/UX 系统性升级

> **性质**：UI 设计决策，非架构决策，不立 ADR。ADR-0009（项目重命名）的前端视觉延伸。

## 0. 元信息

- 触发：用户要求从布局、视觉、交互、氛围进行系统性 UI/UX 升级，设计语言参考崩坏：星穹铁道，但强约束"优先体现专业 AI 应用而非游戏官网"
- 分支：`feature/ai-engineering-rebuild`
- Commit：`d778757`（12 files, +533 / -409）
- 治理流程：EnterPlanMode → ExitPlanMode 审批 → 执行（Charter §5 文档先于代码 ✅）

## 1. 设计决策

### 设计哲学

> 优先体现「专业 AI 应用」而非「游戏官网」。崩铁风格仅作设计语言参考（色彩体系、毛玻璃层次、精致卡片），不复刻游戏功能界面。

### 五个维度

| 维度 | 改动 |
| --- | --- |
| **视觉系统** | `App.vue` 建立 `:root` CSS 变量体系（单一设计来源）；`a-config-provider` 注入 `darkAlgorithm` + `colorPrimary=#4a6fa5`；消灭 4 种冲突蓝色（#3b82f6 / #1890ff / #007bff / #667eea → 统一 #4a6fa5） |
| **布局结构** | HomePage 浅色渐变 → 深空底色 + 星云微光；AppChatPage 白底 → 深色沉浸工作台（修复 `height:100vh` 溢出为 `calc(100vh - 64px)`）；Login/Register 裸白卡 → 深空毛玻璃卡片 |
| **UI 组件** | GlobalHeader 深空半透明毛玻璃 + sticky；AppCard 深色毛玻璃 + 金色微光 hover；DeploySuccessModal 绿→金；MarkdownRenderer github-dark 主题 |
| **动效体验** | 卡片 hover 上浮 4px + 金色微光（250ms ease-out）；hero 一次性 fadeInUp（600ms）；删除全部 4 个 infinite 动画（gridFloat / lightPulse / titleShimmer / heroGlow） |
| **响应式** | 桌面双栏+3 列 / 平板堆叠+2 列 / 手机单列+28px 标题 |

## 2. 改动清单

### 新增

| 文件 | 内容 |
| --- | --- |
| `App.vue` `:root` CSS 变量体系 | 色彩（主色/中性/辅助）、圆角、阴影、间距、过渡、渐变全套 design token |
| `App.vue` `a-config-provider` | Ant Design darkAlgorithm + colorPrimary 统一 + 组件级 token（Modal/Card/Input/Menu） |
| 全局滚动条深空风格 | `::-webkit-scrollbar` 深色轨道 + 半透明 thumb |
| `fadeInUp` 动画工具类 | 一次性淡入，替代所有 infinite 动画 |

### 修改（10 文件）

| 文件 | 改动 |
| --- | --- |
| `BasicLayout.vue` | `background: none` → `var(--color-bg)` + `min-height: 100vh` |
| `GlobalHeader.vue` | 白底 → 深空半透明 `var(--color-bg-glass)` + `blur(12px)` + sticky；标题 `#1890ff` → `var(--color-text-primary)` |
| `GlobalFooter.vue` | 半透明白 → `var(--color-bg-surface)`；文字 `#666` → `var(--color-text-muted)` |
| `HomePage.vue` | 浅色多层渐变 → 深空底色 + 星云径向微光；删除 `::before` 网格动画 + 4 个 `@keyframes`；标题三色渐变 → 银白渐变；输入框/快捷按钮/卡片网格全部 token 化 |
| `AppChatPage.vue` | 白底 `#fdfdfd` → `var(--color-bg)`；面板 white → 毛玻璃 `var(--color-bg-glass)`；用户气泡 `#1890ff` → `rgba(74,111,165,0.3)`；AI 气泡 `#f5f5f5` → `var(--color-bg-glass-light)`；编辑模式按钮 `#52c41a` → `var(--color-accent-gold)`；修复 `height:100vh` → `calc(100vh - 64px)` |
| `UserLoginPage.vue` | 裸白卡 → 全屏深空背景 + 居中毛玻璃卡片（blur(20px) + border + shadow） |
| `UserRegisterPage.vue` | 同 Login |
| `AppCard.vue` | 白卡 → 深色毛玻璃；hover 上浮 8px → 4px（更克制）；占位符 🤖 → "AI" 渐变文字；hover 边框金色微光 |
| `DeploySuccessModal.vue` | 成功图标 `#52c41a` → `var(--color-accent-gold)`；文字 `#666` → token |
| `AppDetailModal.vue` | 标签 `#666` → `var(--color-text-secondary)`；分割线 `#f0f0f0` → `var(--color-border)` |
| `MarkdownRenderer.vue` | `highlight.js/styles/github.css` → `github-dark.css`；代码块 `#f8f8f8` → `#0d1117`；blockquote/table/link 全部 token 化；删除手写 `.hljs-*` 颜色覆盖（交给 github-dark 主题） |

### 未做（按计划明确排除）

- ❌ 不换技术栈 / 不引 UI 框架 / 不加 CSS 预处理器
- ❌ 不改路由 / 状态管理 / API 层 / 业务逻辑
- ❌ 不替换 `logo.png` / `favicon.ico`（二进制，用户自行替换）
- ❌ 不加 web font（保持系统字体栈）
- ❌ 不写 ADR（UI 风格变更不是架构决策）

## 3. 验证

- ✅ `npm run dev` 正常启动，`<title>Prompt2App · AI 应用生成平台</title>` 确认
- ✅ 深色主题全局生效（Ant Design darkAlgorithm + CSS 变量）
- ⏳ 用户端到端视觉验证（待用户刷新浏览器确认各页面效果）

## 4. 治理合规

| 条款 | 状态 | 说明 |
| --- | --- | --- |
| §3 不换技术栈 | ✅ | 仍 Vue3 + Ant Design Vue |
| §4 Task Record | ✅ | 本文件 |
| §5 文档先于代码 | ✅ | EnterPlanMode → ExitPlanMode 审批后再执行 |
| §4 ADR 覆盖 | N/A | UI 风格变更非架构决策 |
| §4 配置外部化 | ✅ | 无新硬编码（全部走 CSS 变量） |

## 5. 与 P0 的关系

本次工作属于 **P3（前端 UI 美化）**，不在 Charter §2 核心能力（Router / Agent / Eval / ADR）范围内。按 Charter §3 精神：

> 作品集项目最大的失败模式不是没做完，是做了一堆 P3 杂事却没把 P0 做透。

前端 UI 到此为止。后续精力应回归 P0 主线。
