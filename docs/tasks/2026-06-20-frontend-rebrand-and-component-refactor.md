# Task Record · 2026-06-20 · 前端去品牌化 + 组件层重构

> **性质**：ADR-0009（项目重命名为 Prompt2App）的前端延伸。不立新 ADR（品牌化不是架构决策）。
> **范围**：去品牌化 + GlobalHeader/Footer 组件重构，不换技术栈、不重写页面。

## 1. 背景

后端 Phase 8 完成配置统一化后，前端暴露多处不适配（URL 拼接、SSE 渲染、预览路径等）。事故链 #6–#10 修复后，前端仍有大量"鱼皮 / 程序员鱼皮 / 编程导航 / codefather.cn"品牌残留，与 ADR-0009 项目重命名不一致。

用户要求：去除所有鱼皮/yupi 元素，统一项目名为 Prompt2App，并对 GlobalHeader / GlobalFooter 做组件层重构。

## 2. 改动清单

### 品牌化文本替换（6 文件）

| 文件 | 旧值 | 新值 |
| --- | --- | --- |
| `index.html` | `鱼皮 AI 零代码应用生成平台` | `Prompt2App · AI 应用生成平台` |
| `GlobalHeader.vue` | `鱼皮应用生成` | `Prompt2App` |
| `GlobalFooter.vue` | `编程导航原创项目 by 程序员鱼皮` + codefather.cn 链接 | `Prompt2App · AI 网页生成平台`（去外链） |
| `UserLoginPage.vue` | `鱼皮 AI 应用生成 - 用户登录` | `Prompt2App · 用户登录` |
| `UserRegisterPage.vue` | `鱼皮 AI 应用生成 - 用户注册` | `Prompt2App · 用户注册` |
| `README.md` | `鱼皮 AI 代码生成器 - 前端` | `Prompt2App · 前端` |

### GlobalHeader 组件重构

- 移除"编程导航"菜单项（`codefather.cn` 外链）
- 站点标题 `鱼皮应用生成` → `Prompt2App`
- 菜单配置清理：3 项（主页 / 用户管理 / 应用管理），不再有外链

### GlobalFooter 组件重构

- 移除 `codefather.cn` 外链 + "程序员鱼皮"文字
- 改为简洁版权：`Prompt2App · AI 网页生成平台`
- 去掉 `.author-link` 样式

### 未做（按计划明确排除）

- ❌ 不替换 `logo.png` / `favicon.ico`（二进制文件，用户自行替换）
- ❌ 不重构页面组件（HomePage / AppChatPage / AppEditPage 等保持现有结构）
- ❌ 不换技术栈（仍 Vue3 + TS + Ant Design Vue）
- ❌ 不改路由 / 状态管理 / API 层
- ❌ 不写 ADR（品牌化是 ADR-0009 延伸）

## 3. 验证

- ✅ `grep -rn "鱼皮\|yupi\|codefather\|编程导航\|程序员鱼皮"` 全前端零匹配
- ✅ `npm run dev` 正常启动，`<title>Prompt2App · AI 应用生成平台</title>` 确认生效

## 4. 前序不适配修复（已在前序 commit 完成，本次确认无遗漏）

| 问题 | 修复 commit |
| --- | --- |
| `getDeployUrl` 走 DEPLOY_DOMAIN（80 端口 nginx 残留） | `11b6955`（改用 STATIC_BASE_URL） |
| SSE 渲染阻塞主线程 | `053b0c3`（requestAnimationFrame 节流） |
| 预览 404（StaticResourceController 只读 deployDir） | `267723b`（双目录 fallback） |

## 5. 治理合规

- ✅ Charter §3「React/Next.js 迁移不做」——不换技术栈
- ✅ Charter §4「Task Record」——本文件
- ✅ Charter §5「文档先于代码」——计划先审批（EnterPlanMode → ExitPlanMode）再执行
- ✅ 不涉及后端改动，不需要 mvn 验证
