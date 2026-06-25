# Task: MultiFile 孤儿注释生成空壳 HTML 文件修复

- **日期**：2026-06-26
- **Phase**：Eval 增量（生产 bug）
- **责任人**：项目作者 + AI
- **状态**：Done（2026-06-26 单测 7/7 + eval 包 72/72 全绿 + 离线验证通过）
- **关联 ADR**：无（bug 修复，非架构变更）
- **关联 task**：[`2026-06-21-multi-file-vue-eval-fix.md`](./2026-06-21-multi-file-vue-eval-fix.md)（上一轮修了 MULTI_FILE 三层根因，本次是同源残留）

---

## 1. 目标

修复 MultiFile 代码生成时，LLM 输出的孤儿文件名注释（`<!-- index.html -->` 后面无 `<!DOCTYPE`）被当作独立"页"落盘，产生 19-22 字节空壳 HTML 文件的 bug。

## 2. 背景

### 2.1 触发现象

用户（生产环境，appId=427802664814944256）调 MULTI_FILE 生成"塞勒姆图鉴"（异世界生物百科多页站），产物目录 `tmp/code_output/multi_file_427802664814944256/` 含 11 个文件：

| 文件 | DOCTYPE | 首行注释 | size | 状态 |
| --- | --- | --- | --- | --- |
| 关于塞勒姆图鉴.html | ✅ | ❌ | 3445 | ✅ 正常 |
| 图鉴塞勒姆生物百科.html | ✅ | ❌ | 2254 | ✅ 正常 |
| 塞勒姆图鉴异世界生物百科.html | ✅ | ❌ | 1946 | ✅ 正常 |
| 生物详情塞勒姆图鉴.html | ✅ | ❌ | 1426 | ✅ 正常 |
| **index.html** | ❌ | ✅ | **19** | 🐛 空壳 |
| **about.html** | ❌ | ✅ | **19** | 🐛 空壳 |
| **bestiary.html** | ❌ | ✅ | **22** | 🐛 空壳 |
| **creature.html** | ❌ | ✅ | **22** | 🐛 空壳 |
| script.js | — | ✅ | 14783 | ✅ 正常 |
| style.css | — | ✅ | 15629 | ✅ 正常 |

4 个空壳 html 内容**只有注释本身**（如 `<!-- index.html -->`），无实际 HTML。

### 2.2 LLM 输出结构推断

```
```html
<!-- index.html -->              ← 孤儿注释（LLM 先列文件清单？）
<!-- about.html -->              ← 孤儿注释
<!-- bestiary.html -->           ← 孤儿注释
<!-- creature.html -->           ← 孤儿注释
<!DOCTYPE html>...真实内容1...    ← 第 1 页（title=关于塞勒姆图鉴）
<!DOCTYPE html>...真实内容2...    ← 第 2 页
<!DOCTYPE html>...真实内容3...    ← 第 3 页
<!DOCTYPE html>...真实内容4...    ← 第 4 页
```
```

### 2.3 根因

`PAGE_SPLIT_PATTERN`（parser + saver 共用）用 lookahead `(?=...)` 按"注释 + DOCTYPE"锚点拆分：

```java
Pattern PAGE_SPLIT_PATTERN = Pattern.compile(
    "(?=(?:<!--\\s*[\\w.-]+\\.html\\s*-->\\s*)?<!DOCTYPE\\s*html>)",
    Pattern.CASE_INSENSITIVE);
```

注释部分是**可选的**（`?`），所以拆分锚点是"DOCTYPE 前可能有注释"。但 LLM 输出的孤儿注释（无后续 DOCTYPE）被 split 当成**独立段落**，每个段落只有注释本身 → 被 `if (!page.isEmpty())` 放行 → 落盘成空壳。

**两条路径都受影响**：
- `MultiFileCodeParser.splitIfMultiPage`（parser 路径，第 68-78 行）
- `MultiFileCodeFileSaverTemplate.splitHtmlPages` 的 fallback DOCTYPE 分支（第 68-77 行）

## 3. 影响范围（Scope）

**改动文件**：

| 文件 | 改动 |
| --- | --- |
| `src/main/java/com/prompt2app/agent/codegen/parser/MultiFileCodeParser.java` | `splitIfMultiPage` 加 filter：丢弃不含 `<!DOCTYPE` 的孤儿段落 |
| `src/main/java/com/prompt2app/agent/codegen/saver/MultiFileCodeFileSaverTemplate.java` | `splitHtmlPages` 两条路径都加 filter：丢弃孤儿注释页 |
| `src/test/java/com/prompt2app/agent/codegen/saver/MultiFileCodeFileSaverTemplateTest.java`（新建） | 覆盖：孤儿注释 + 正常多页 + 单页 + 空输入 |
| `src/test/java/com/prompt2app/agent/codegen/parser/MultiFileCodeParserTest.java`（新建或追加） | 覆盖 parser 路径的孤儿注释 filter |

**明确不做**（防 scope 蔓延）：
- ❌ 不改 `PAGE_SPLIT_PATTERN` 正则本身（方案 B/C 超出最小修复 scope）
- ❌ 不动 HtmlCodeParser / VueProjectBuilder
- ❌ 不动 25 case yaml
- ❌ 不重跑全量评测（用本次真实产物离线验证即可）

## 4. 修改内容

### 4.1 方案 A：filter 丢弃孤儿注释页

**核心判断**：一个"页"是有效的当且仅当它含 `<!DOCTYPE`（HTML 页必须有 DOCTYPE 才是完整页）。

**parser 侧**（`MultiFileCodeParser.splitIfMultiPage`）：

```java
private List<String> splitIfMultiPage(String htmlBlock) {
    String[] parts = PAGE_SPLIT_PATTERN.split(htmlBlock);
    List<String> pages = new ArrayList<>();
    for (String part : parts) {
        String trimmed = part.trim();
        // 方案 A：丢弃孤儿注释页（无 <!DOCTYPE 的段落）
        if (!trimmed.isEmpty() && trimmed.toLowerCase().contains("<!doctype")) {
            pages.add(trimmed);
        }
    }
    return pages;
}
```

**saver 侧**（`MultiFileCodeFileSaverTemplate.splitHtmlPages`）两条路径都加同样的 filter：

```java
// FILE_SEPARATOR 路径
for (int i = 0; i < parts.length; i++) {
    String page = parts[i].trim();
    if (!page.isEmpty() && page.toLowerCase().contains("<!doctype")) {
        pages.add(new PageEntry(resolveHtmlFileName(page, i), page));
    }
}

// fallback DOCTYPE 路径
for (int i = 0; i < doctypeParts.length; i++) {
    String page = doctypeParts[i].trim();
    if (!page.isEmpty() && page.toLowerCase().contains("<!doctype")) {
        pages.add(new PageEntry(resolveHtmlFileName(page, i), page));
    }
}
```

### 4.2 边界情况

| 输入 | 修复前 | 修复后 |
| --- | --- | --- |
| 4 孤儿注释 + 4 DOCTYPE 页 | 8 个文件（4 空壳 + 4 正常）| 4 个文件（4 正常）✅ |
| 只有孤儿注释（无 DOCTYPE）| 4 个空壳 | 0 个文件 → `validateInput` 抛 "HTML代码内容不能为空" ✅ |
| 单页（1 个 DOCTYPE）| 1 个文件 | 1 个文件 ✅ |
| 无注释 + 多 DOCTYPE | N 个文件 | N 个文件 ✅ |

### 4.3 为什么不改正则（方案 B/C 不选）

改正则 `(?=...)` 去掉 `?` 让注释变必需——但有些 LLM 输出**不带注释直接 DOCTYPE**（本次 4 个正常页就是无注释的），改正则会漏掉这些正常页。filter 方案对"有无注释"都兼容，更稳。

## 5. 验证

- [x] 通用 DoD
- [x] `MultiFileCodeFileSaverTemplateTest`（新建）：**4/4 通过**
  - 孤儿注释 + 正常多页 → 只落盘 4 个真实页（不含空壳）✅
  - 单页（1 DOCTYPE）→ 1 个文件 ✅
  - FILE_SEPARATOR 路径 + 孤儿注释 → 只存真实页 ✅
  - 只有孤儿注释 → 0 个 html 文件 ✅
- [x] `MultiFileCodeParserTest`（新建）：**3/3 通过**
  - 孤儿注释被 filter（FILE_SEPARATOR 数 = 3，4 个真实页）✅
  - 单页正确 ✅
  - CSS/JS 分离提取 ✅
- [x] 编译 + eval + codegen 包 **72 测试全绿**，无退化
- [x] 离线验证：模拟 bug 输入（4 孤儿注释 + 4 DOCTYPE）→ 修复前 6 段（含 2 空壳）→ 修复后 4 段（0 空壳）✅

## 6. 风险与遗留

- **已知风险**：filter 用 `contains("<!doctype")`（大小写不敏感）——如果 LLM 生成非标准 HTML（如 `<html>` 无 DOCTYPE），会被误丢。但 MULTI_FILE 策略要求完整 HTML 页，无 DOCTYPE 本就不合格，丢弃合理。
- **遗留事项**：
  - 本次 bug 在评测中也出现过（case 015-university 的 19 字节空壳 index.html），但因 Render veto 未被深查——本修复同时解决那个评测场景
  - LLM 为什么会输出孤儿注释（先列清单再写内容）？这是 prompt 模板问题，独立 backlog，不在本 scope

## 7. 对治理体系的更新

- [x] 本 Task Record
- [ ] 完成后更新 `docs/tasks/README.md` 索引
- [ ] 完成后更新 `docs/roadmap/current-phase.md` backlog

## 8. 实施顺序

1. 改 `MultiFileCodeParser.splitIfMultiPage` 加 filter
2. 改 `MultiFileCodeFileSaverTemplate.splitHtmlPages` 两条路径加 filter
3. 新建 `MultiFileCodeFileSaverTemplateTest`（4 用例）
4. 新建/追加 `MultiFileCodeParserTest`（parser 路径）
5. 编译 + eval 包全量单测
6. 离线验证（用真实产物）
7. 回填 Task Record §5 + 治理文档
