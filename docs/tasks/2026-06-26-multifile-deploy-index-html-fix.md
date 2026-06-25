# Task: MULTI_FILE 部署后无 index.html 导致访问 404 修复

- **日期**：2026-06-26
- **Phase**：生产 bug
- **责任人**：项目作者 + AI
- **状态**：Done（2026-06-26 单测 4/4 + eval+codegen 76/76 全绿 + 离线验证通过）
- **关联 ADR**：[ADR-0012](../adr/0012-static-resource-dual-dir-and-url-unification.md)（静态资源双目录 fallback + URL 策略统一）
- **关联 task**：[`2026-06-26-multifile-orphan-comment-fix.md`](./2026-06-26-multifile-orphan-comment-fix.md)（同日 MultiFile 修复，不同根因）

---

## 1. 目标

修复 MULTI_FILE 应用部署后，访问 `/{deployKey}/` 返回 404 的 bug——部署目录没有 `index.html` 默认页，StaticResourceController 默认找 `index.html` 找不到。

## 2. 背景

### 2.1 触发现象

用户生成 MULTI_FILE 应用"米哈游角色图鉴"（appId=427809049162199040），部署后访问部署 URL 返回 404。

### 2.2 产物目录

`tmp/code_output/multi_file_427809049162199040/`：

| 文件 | size | 含 `<!DOCTYPE` |
| --- | --- | --- |
| 米哈游角色图鉴-关于.html | 3275 | ✅ |
| 米哈游角色图鉴-角色详情.html | 1676 | ✅ |
| 米哈游角色图鉴-首页.html | 2459 | ✅ |
| script.js | 13249 | — |
| style.css | 10016 | — |

**3 个 HTML 文件名都是中文 `<title>` 提取的，没有 `index.html`。**

### 2.3 根因

`StaticResourceController.serveStaticResource` 第 66-68 行：

```java
if (resourcePath.equals("/")) {
    resourcePath = "/index.html";   // ← 默认找 index.html
}
```

部署后访问 `/{deployKey}/` → 找 `codeDeployDir/{deployKey}/index.html` → **不存在** → 404。

`MultiFileCodeFileSaverTemplate.resolveHtmlFileName` 从 `<title>` 提取文件名（如"米哈游角色图鉴-首页"），LLM 生成的 title 几乎不会是英文 "index"，所以 MULTI_FILE 产物**几乎从不生成 `index.html`**。

### 2.4 与上一个 task 的关系

上一个 task（2026-06-26 孤儿注释修复）解决的是"空壳 HTML 文件"。本 task 解决的是"没有默认入口页"。**两个根因独立**——即使没有空壳 bug，MULTI_FILE 产物也不会有 `index.html`。

## 3. 影响范围（Scope）

**改动文件**：

| 文件 | 改动 |
| --- | --- |
| `src/main/java/com/prompt2app/app/service/impl/AppServiceImpl.java` | `deployApp` 拷文件后兜底 index.html |

**明确不做**：
- ❌ 不改 saver 落盘逻辑（产物保持原文件名，中文 title 有可读性）
- ❌ 不改 StaticResourceController（默认 index.html 是标准行为，不改）
- ❌ 不改前端（本 task 只修后端）
- ❌ 不动 25 case yaml
- ❌ 不夹带问题 1（预览 URL）的修复

## 4. 修改内容

### 4.1 方案 A：部署时兜底 index.html

`deployApp` 第 196 行 `FileUtil.copyContent` 之后，加兜底逻辑：

```java
// 8. 复制文件到部署目录
String deployDirPath = ...;
FileUtil.copyContent(sourceDir, new File(deployDirPath), true);

// 8.1 兜底：MULTI_FILE 产物文件名是 <title> 提取的中文，没有 index.html
//     部署后访问 /{deployKey}/ 默认找 index.html，需复制一个入口页
ensureIndexHtml(new File(deployDirPath));
```

### 4.2 ensureIndexHtml 逻辑

```java
/**
 * 确保部署目录有 index.html 作为默认入口页。
 * <p>选择优先级：
 * <ol>
 *   <li>已有 index.html → 不动</li>
 *   <li>title 含"首页"/"index"/"home"的 HTML → 复制为 index.html</li>
 *   <li>第一个 HTML 文件（按字母序）→ 复制为 index.html</li>
 * </ol>
 */
private void ensureIndexHtml(File deployDir) {
    File indexFile = new File(deployDir, "index.html");
    if (indexFile.exists()) return;  // 已有，不重复

    File[] htmls = deployDir.listFiles((d, n) -> n.endsWith(".html"));
    if (htmls == null || htmls.length == 0) return;  // 无 HTML，无法兜底

    // 优先 title 含"首页"/"index"/"home"
    File chosen = null;
    for (File f : htmls) {
        String name = f.getName().toLowerCase();
        if (name.contains("首页") || name.contains("index") || name.contains("home")) {
            chosen = f;
            break;
        }
    }
    // 其次第一个 HTML
    if (chosen == null) chosen = htmls[0];

    FileUtil.copy(chosen, indexFile);
    log.info("[Deploy] 兜底 index.html: 复制 {} → index.html", chosen.getName());
}
```

### 4.3 只对 MULTI_FILE 生效？

HTML 策略产物文件名固定 `index.html`（`HtmlCodeFileSaverTemplate` 第 30 行），不需要兜底。VUE_PROJECT 部署时拷 `dist/`，vite build 一定产出 `dist/index.html`，也不需要兜底。**只有 MULTI_FILE 需要**。

但 `ensureIndexHtml` 加了"已有 index.html 就不动"的守卫，对 HTML / VUE 也无害（直接 return）。所以**不额外加 if 判断 codeGenType**，统一走 ensureIndexHtml 即可——简洁且防御性。

## 5. 验证

- [x] 通用 DoD
- [x] `AppServiceImplEnsureIndexHtmlTest`（新建）：**4/4 通过**
  - 已有 index.html → 不动 ✅
  - 有"首页"标题 → 复制为 index.html ✅
  - 无"首页"标题 → 复制第一个 HTML（字母序）✅
  - 空目录 → 不抛异常 ✅
- [x] eval + codegen + 新单测 **76 测试全绿**，无退化
- [x] 离线验证：真实产物 `multi_file_427809049162199040` 模拟部署 → copyContent 后无 index.html → ensureIndexHtml 后生成 index.html（2459B，从"米哈游角色图鉴-首页.html"复制）✅

## 6. 风险与遗留

- **已知风险**：兜底 index.html 是**复制**不是重命名——产物目录保持原文件名（中文 title 有可读性），部署目录多一个 index.html 副本。磁盘占用微小，可接受。
- **遗留事项**：
  - 问题 1（生成后预览 URL 断链）——独立 task，需改前端
  - LLM 为什么不输出 index 标题——prompt 模板问题，独立 backlog

## 7. 对治理体系的更新

- [x] 本 Task Record
- [ ] 完成后更新 `docs/tasks/README.md` 索引

## 8. 实施顺序

1. 改 `AppServiceImpl.deployApp` 加 `ensureIndexHtml` 兜底
2. 新建 `AppServiceImplTest`（4 用例）
3. 编译 + eval 包全量单测
4. 离线验证（真实产物）
5. 回填 Task Record + 治理文档 + commit/push
