# Task: MultiFile split 注释与 DOCTYPE 分离导致文件名与链接不匹配修复

- **日期**：2026-06-26
- **Phase**：生产 bug
- **责任人**：项目作者 + AI
- **状态**：Done（2026-06-26 单测 10/10 + 全量 79/79 + 离线验证 5/5 文件名匹配）
- **关联 ADR**：无（bug 修复，非架构变更）
- **关联 task**：
  - [`2026-06-26-multifile-orphan-comment-fix.md`](./2026-06-26-multifile-orphan-comment-fix.md)（同日早些修了"空壳文件"——丢弃无 DOCTYPE 的孤儿注释段；本次发现该 filter 会让注释丢文件名）
  - [`2026-06-26-multifile-deploy-index-html-fix.md`](./2026-06-26-multifile-deploy-index-html-fix.md)（部署侧 index.html 兜底）

---

## 1. 目标

修复 MultiFile 生成时，`PAGE_SPLIT_PATTERN` 的 lookahead split 把 `<!-- filename.html -->\n<!DOCTYPE html>` **拆成两段**（注释段 + DOCTYPE 段），导致：
- 注释段被上次的 DOCTYPE filter 丢弃 → 文件名信息丢失
- DOCTYPE 段没有注释 → saver 从 `<title>` 提取中文文件名
- 中文文件名和 LLM 写的链接（`href="genshin.html"`）不匹配 → 路由 404

## 2. 背景

### 2.1 触发现象

用户生成 MULTI_FILE"米哈游角色图鉴"（appId=427814509294518272），5 个 HTML 页面之间的导航链接全部 404。

### 2.2 LLM 输出（完全正确）

LLM 每个页都加了文件名注释 + 用同名链接：
```
<!-- index.html -->       ← 注释文件名
<!DOCTYPE html>          ← DOCTYPE
<html>...<title>米哈游角色图鉴 | 首页</title>...
href="genshin.html"      ← 链接文件名 = 注释文件名 ✅
```

5 个页的注释文件名：`index.html` / `genshin.html` / `honkai3.html` / `starrail.html` / `zzz.html`
链接全部一致：`href="index.html"` / `href="genshin.html"` / 等

**LLM 输出完全自洽**——注释文件名 = 链接文件名。

### 2.3 实际产物（错误）

| 注释文件名（LLM 期望）| 实际文件名（saver 存的）| 链接指向 |
| --- | --- | --- |
| index.html | 米哈游角色图鉴首页.html | index.html ❌ 不匹配 |
| genshin.html | 原神角色图鉴米哈游图鉴.html | genshin.html ❌ |
| honkai3.html | 崩坏3角色图鉴米哈游图鉴.html | honkai3.html ❌ |
| starrail.html | 星穹铁道角色图鉴米哈游图鉴.html | starrail.html ❌ |
| zzz.html | 绝区零角色图鉴米哈游图鉴.html | zzz.html ❌ |

### 2.4 根因（split 把注释和 DOCTYPE 拆开了）

`PAGE_SPLIT_PATTERN`（parser + saver 共用）：
```java
Pattern PAGE_SPLIT_PATTERN = Pattern.compile(
    "(?=(?:<!--\\s*[\\w.-]+\\.html\\s*-->\\s*)?<!DOCTYPE\\s*html>)",
    Pattern.CASE_INSENSITIVE);
```

这是 **lookahead split**——它在每个"注释 + DOCTYPE"锚点**之前**切一刀。结果：

| 段 | 内容 | DOCTYPE? | filter 后 |
| --- | --- | --- | --- |
| 段 1 | `<!-- index.html -->` | ❌ | 被上次 filter 丢弃 |
| 段 2 | `<!DOCTYPE html>...<title>米哈游...</title>...` | ✅ | 保留，但注释丢了 → 从 title 提中文名 |
| 段 3 | `<!-- genshin.html -->` | ❌ | 丢弃 |
| 段 4 | `<!DOCTYPE html>...<title>原神...</title>...` | ✅ | 保留，注释丢了 → 中文名 |

**关键矛盾**：上次的 filter（丢弃无 DOCTYPE 段）和这次的"注释要跟着 DOCTYPE 走"是**对立的**——filter 丢注释导致文件名丢失。

### 2.5 与上一个 task 的关系

[`2026-06-26-multifile-orphan-comment-fix.md`](./2026-06-26-multifile-orphan-comment-fix.md) 修了"空壳文件"——用 filter 丢弃无 DOCTYPE 的段。那个修复**对真正的孤儿注释（无后续 DOCTYPE 的清单式注释）是对的**。但本次发现：**注释 + DOCTYPE 的正常组合也被 split 拆开了**，filter 把注释段丢了。

两个 bug 的区别：
- 上次：孤儿注释（LLM 先列 4 个注释再写 4 个 DOCTYPE 页）→ filter 丢孤儿 ✅
- 本次：注释紧贴 DOCTYPE（`<!-- x.html -->\n<!DOCTYPE>`）→ split 拆开 → 注释被误丢 ❌

## 3. 影响范围（Scope）

**改动文件**：

| 文件 | 改动 |
| --- | --- |
| `src/main/java/com/prompt2app/agent/codegen/parser/MultiFileCodeParser.java` | `splitIfMultiPage` 改用"匹配完整页"正则（含可选注释前缀），不再用 lookahead split + filter |
| `src/main/java/com/prompt2app/agent/codegen/saver/MultiFileCodeFileSaverTemplate.java` | `splitHtmlPages` fallback 路径同样改用"匹配完整页"正则 |
| `src/test/java/com/prompt2app/agent/codegen/parser/MultiFileCodeParserTest.java` | 追加用例：注释紧贴 DOCTYPE → 文件名从注释提取 |
| `src/test/java/com/prompt2app/agent/codegen/saver/MultiFileCodeFileSaverTemplateTest.java` | 追加用例：注释紧贴 DOCTYPE → 文件名 = 注释名（非中文 title）|

**明确不做**：
- ❌ 不改 `ensureIndexHtml`（上次的兜底仍保留——防御无注释的 LLM 输出）
- ❌ 不改 25 case yaml
- ❌ 不改 prompt 模板（LLM 输出本身正确）
- ❌ 不动 HtmlCodeParser / VueProjectBuilder

## 4. 修改内容

### 4.1 核心思路：从"split + filter"改为"匹配完整页"

旧逻辑（split + filter）：
```java
// split 在锚点前切一刀 → 注释和 DOCTYPE 分到两段 → filter 丢注释
String[] parts = PAGE_SPLIT_PATTERN.split(htmlCode);
for (String part : parts) {
    if (part.contains("<!doctype")) pages.add(part);  // 注释段被丢
}
```

新逻辑（正则匹配完整页，含可选注释前缀）：
```java
// 一个"页"= 可选注释 + DOCTYPE + 到下一个锚点前的所有内容
Pattern COMPLETE_PAGE_PATTERN = Pattern.compile(
    "(<!--\\s*[\\w.-]+\\.html\\s*-->)?\\s*<!DOCTYPE\\s*html>[\\s\\S]*?"
    + "(?=(?:<!--\\s*[\\w.-]+\\.html\\s*-->)?\\s*<!DOCTYPE\\s*html>|$)",
    Pattern.CASE_INSENSITIVE);
```

匹配结果：
- `<!-- index.html -->\n<!DOCTYPE html>...` → 一段，**注释跟着 DOCTYPE** ✅
- `<!-- genshin.html -->\n<!DOCTYPE html>...` → 一段 ✅
- 真正的孤儿注释（无后续 DOCTYPE）→ 不匹配 COMPLETE_PAGE_PATTERN → 自然不出现 ✅

### 4.2 filter 逻辑变化

- **旧**：filter 丢弃无 DOCTYPE 的段（防空壳）
- **新**：正则只匹配含 DOCTYPE 的完整页，孤儿注释天然不匹配 → 不需要 filter

上次的 filter（`contains("<!doctype")`）**可以移除**——COMPLETE_PAGE_PATTERN 已经保证每段都有 DOCTYPE。

### 4.3 resolveHtmlFileName 不变

`saver` 的 `resolveHtmlFileName` 优先级不变：
1. `<!-- filename.html -->` 注释（现在注释跟着 DOCTYPE 段，能提取到）✅
2. `<title>` 提取（fallback）

注释能提取到 → 文件名 = `genshin.html`（和链接一致）✅

### 4.4 parser 侧

`MultiFileCodeParser.splitIfMultiPage` 同样改用 COMPLETE_PAGE_PATTERN。

### 4.5 兼容性

- LLM 输出无注释（直接 DOCTYPE）→ COMPLETE_PAGE_PATTERN 的注释部分是可选的（`(<!--...-->?)`）→ 仍能匹配 → 从 title 提取 ✅
- LLM 输出有注释 → 注释跟着 DOCTYPE → 从注释提取 ✅
- 孤儿注释（无 DOCTYPE）→ 不匹配 → 不出现 ✅（替代了上次的 filter）

## 5. 验证

- [ ] 通用 DoD
- [ ] `MultiFileCodeParserTest` 追加：注释紧贴 DOCTYPE → 文件名从注释提取（非中文 title）
- [ ] `MultiFileCodeFileSaverTemplateTest` 追加：注释紧贴 DOCTYPE → 落盘文件名 = 注释名（如 `genshin.html`，非中文）
- [ ] 旧用例不退化：孤儿注释（清单式）仍被丢弃、单页正确、CSS/JS 分离
- [ ] 编译 + eval + codegen 包全量测试
- [ ] 离线验证：用本次真实 LLM 输出（5 页 + 注释）模拟 → 文件名 = 注释名

## 6. 风险与遗留

- **已知风险**：COMPLETE_PAGE_PATTERN 用 `[\s\S]*?` 非贪婪 + lookahead 结束——如果 HTML 内容里含 `<!DOCTYPE`（罕见但理论可能），会提前截断。但 MULTI_FILE 的 LLM 输出不会在页内容里嵌 DOCTYPE，风险可接受。
- **遗留事项**：
  - `ensureIndexHtml` 兜底仍保留（防御无注释输出，无害）
  - 上次 filter 的"空壳文件"修复仍有效（COMPLETE_PAGE_PATTERN 天然不匹配孤儿注释）

## 7. 对治理体系的更新

- [x] 本 Task Record
- [ ] 完成后更新 `docs/tasks/README.md` 索引

## 8. 实施顺序

1. 改 `MultiFileCodeParser.splitIfMultiPage` 用 COMPLETE_PAGE_PATTERN
2. 改 `MultiFileCodeFileSaverTemplate.splitHtmlPages` fallback 路径用 COMPLETE_PAGE_PATTERN
3. 移除两处的 `contains("<!doctype")` filter（COMPLETE_PAGE_PATTERN 替代）
4. 追加单测（注释紧贴 DOCTYPE → 文件名从注释提取）
5. 编译 + 全量单测
6. 离线验证（真实 LLM 输出）
7. 回填 Task Record + 治理文档 + commit/push
