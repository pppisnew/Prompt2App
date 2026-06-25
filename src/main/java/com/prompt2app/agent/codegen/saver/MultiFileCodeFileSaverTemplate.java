package com.prompt2app.agent.codegen.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.prompt2app.agent.model.MultiFileCodeResult;
import com.prompt2app.infra.exception.BusinessException;
import com.prompt2app.infra.exception.ErrorCode;
import com.prompt2app.app.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码保存器（多 HTML 页面 + CSS + JS）。
 *
 * <p>htmlCode 字段可能含多个 HTML 页面（以 FILE_SEPARATOR 分隔），拆分为独立文件保存。
 * 如果无法拆分（旧格式单 HTML），则保存为 index.html。
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {

    private static final String FILE_SEPARATOR = "<!-- ===== FILE_SEPARATOR ===== -->";
    private static final Pattern TITLE_TAG_PATTERN = Pattern.compile(
            "<title>\\s*(.*?)\\s*</title>", Pattern.CASE_INSENSITIVE);

    public MultiFileCodeFileSaverTemplate(String fileSaveRootDir) {
        super(fileSaveRootDir);
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        String htmlCode = result.getHtmlCode();
        // 直接按 <!DOCTYPE + <!-- filename.html --> 拆分多页 HTML
        // （LangChain4j @StructuredOutput 不经过 parser，htmlCode 可能含多个 DOCTYPE）
        List<PageEntry> pages = splitHtmlPages(htmlCode);
        for (int i = 0; i < pages.size(); i++) {
            PageEntry page = pages.get(i);
            writeToFile(baseDirPath, page.fileName, page.content);
        }
        // 保存 CSS 文件
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        // 保存 JavaScript 文件
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        // 兜底：MULTI_FILE 产物文件名从 <title> 提取中文（如"米哈游角色图鉴-首页.html"），
        // 没有 index.html。但 StaticResourceController 预览 /{key}/ 默认找 index.html → 404。
        // 选择优先级：已有 index.html → 不动；文件名含"首页"/"index"/"home" → 复制；否则第一个 HTML。
        ensureIndexHtml(baseDirPath);
    }

    /** 确保目录有 index.html 作为默认入口页（预览 + 部署共用）。 */
    private void ensureIndexHtml(String baseDirPath) {
        File indexFile = new File(baseDirPath, "index.html");
        if (indexFile.exists()) {
            return;  // 已有，不重复
        }
        File dir = new File(baseDirPath);
        File[] htmls = dir.listFiles((d, n) -> n.endsWith(".html"));
        if (htmls == null || htmls.length == 0) {
            return;  // 无 HTML，无法兜底
        }
        // 优先：文件名含"首页"/"index"/"home"
        File chosen = null;
        for (File f : htmls) {
            String name = f.getName().toLowerCase();
            if (name.contains("首页") || name.contains("index") || name.contains("home")) {
                chosen = f;
                break;
            }
        }
        // 其次：第一个 HTML（按字母序，保证确定性）
        if (chosen == null) {
            java.util.Arrays.sort(htmls, java.util.Comparator.comparing(File::getName));
            chosen = htmls[0];
        }
        FileUtil.copy(chosen.toPath(), indexFile.toPath());
    }

    /** 按页拆分 HTML（支持 <!-- filename.html --> + <!DOCTYPE 双重信号）。 */
    private List<PageEntry> splitHtmlPages(String htmlCode) {
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            return List.of();
        }
        // 先按 FILE_SEPARATOR 拆（parser 注入的，如果走了 parser 路径）
        if (htmlCode.contains(FILE_SEPARATOR)) {
            String[] parts = htmlCode.split(FILE_SEPARATOR);
            List<PageEntry> pages = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                String page = parts[i].trim();
                if (!page.isEmpty()) {
                    pages.add(new PageEntry(resolveHtmlFileName(page, i), page));
                }
            }
            return pages;
        }
        // Fallback：用"完整页"正则匹配（注释跟着 DOCTYPE 走，2026-06-26 split 注释分离修复）
        // 替代旧的 PAGE_SPLIT_PATTERN split + filter——旧方案把 <!-- x.html --> 和 <!DOCTYPE> 拆开
        Matcher matcher = COMPLETE_PAGE_PATTERN.matcher(htmlCode);
        List<PageEntry> pages = new ArrayList<>();
        int i = 0;
        while (matcher.find()) {
            String page = matcher.group().trim();
            if (!page.isEmpty()) {
                pages.add(new PageEntry(resolveHtmlFileName(page, i), page));
                i++;
            }
        }
        return pages;
    }

    /** 页面条目：文件名 + 内容。 */
    private record PageEntry(String fileName, String content) {}

    /** 文件名注释模式：<!-- index.html --> 或 <!-- about.html --> */
    private static final Pattern FILE_NAME_COMMENT_PATTERN = Pattern.compile(
            "^\\s*<!--\\s*([\\w.-]+\\.html)\\s*-->");

    /** 按 <!DOCTYPE html> 拆分多页（lookahead，保留分隔符在结果里） */
    private static final Pattern PAGE_SPLIT_PATTERN = Pattern.compile(
            "(?=(?:<!--\\s*[\\w.-]+\\.html\\s*-->\\s*)?<!DOCTYPE\\s*html>)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 完整页正则：可选注释前缀 + DOCTYPE + 到下一个锚点前的内容（2026-06-26 split 注释分离修复）。
     *
     * <p>替代 {@link #PAGE_SPLIT_PATTERN} 的 lookahead split——旧 split 把
     * {@code <!-- x.html -->\n<!DOCTYPE>} 拆成两段，注释段被丢导致文件名从 title 提取中文。
     * 新正则匹配完整页，注释跟着 DOCTYPE 段走，{@link #resolveHtmlFileName} 能提取到注释文件名。
     */
    static final Pattern COMPLETE_PAGE_PATTERN = Pattern.compile(
            "(?:<!--\\s*[\\w.-]+\\.html\\s*-->\\s*)?<!DOCTYPE\\s*html>[\\s\\S]*?"
            + "(?=(?:<!--\\s*[\\w.-]+\\.html\\s*-->\\s*)?<!DOCTYPE\\s*html>|\\Z)",
            Pattern.CASE_INSENSITIVE);

    /** 从 <title> 标签提取文件名，提取失败则用 page_0.html / page_1.html ... */
    private String resolveHtmlFileName(String htmlContent, int index) {
        // 优先：检查开头的 <!-- filename.html --> 注释（LLM 常加）
        Matcher commentMatcher = FILE_NAME_COMMENT_PATTERN.matcher(htmlContent);
        if (commentMatcher.find()) {
            return commentMatcher.group(1).toLowerCase();
        }
        // 其次：从 <title> 标签提取
        Matcher m = TITLE_TAG_PATTERN.matcher(htmlContent);
        if (m.find()) {
            String title = m.group(1).trim()
                    .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "")
                    .replaceAll("\\s+", "_");
            if (!title.isEmpty()) {
                return title.toLowerCase() + ".html";
            }
        }
        return index == 0 ? "index.html" : "page_" + index + ".html";
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}