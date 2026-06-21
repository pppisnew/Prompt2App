package com.prompt2app.agent.codegen.parser;

import com.prompt2app.agent.model.MultiFileCodeResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码解析器（多 HTML 页面 + CSS + JS）。
 *
 * <p>支持两种 LLM 输出模式：
 * <ol>
 *   <li>理想模式：每个 HTML 页面一个独立的 ```html 代码块</li>
 *   <li>Fallback 模式：所有页面在一个 ```html 代码块里，用 &lt;!-- filename.html --&gt; +
 *       &lt;!DOCTYPE&gt; 分隔。DeepSeek 实测倾向于此模式。</li>
 * </ol>
 */
public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** Fallback 拆分信号：HTML 文件注释 + DOCTYPE */
    private static final Pattern PAGE_SPLIT_PATTERN = Pattern.compile(
            "(?=(?:<!--\\s*[\\w.-]+\\.html\\s*-->\\s*)?<!DOCTYPE\\s*html>)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 提取所有 HTML 代码块
        List<String> htmlBlocks = extractAllBlocks(codeContent, HTML_CODE_PATTERN);
        // 对每个 HTML 代码块做 fallback 拆分（含多个 DOCTYPE 时拆为多页）
        List<String> htmlPages = new ArrayList<>();
        for (String block : htmlBlocks) {
            htmlPages.addAll(splitIfMultiPage(block));
        }
        // 合并为 htmlCode 字段（saver 会按 FILE_SEPARATOR 拆分保存）
        StringBuilder htmlBuilder = new StringBuilder();
        for (int i = 0; i < htmlPages.size(); i++) {
            if (htmlBuilder.length() > 0) {
                htmlBuilder.append("\n<!-- ===== FILE_SEPARATOR ===== -->\n");
            }
            htmlBuilder.append(htmlPages.get(i).trim());
        }
        if (htmlBuilder.length() > 0) {
            result.setHtmlCode(htmlBuilder.toString());
        }

        String cssCode = extractFirstBlock(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractFirstBlock(codeContent, JS_CODE_PATTERN);
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    /**
     * 如果一个 HTML 代码块里含多个 <!DOCTYPE html>，按 DOCTYPE 拆分为多页。
     * 如果只有一个 DOCTYPE，返回单元素列表（不拆）。
     */
    private List<String> splitIfMultiPage(String htmlBlock) {
        String[] parts = PAGE_SPLIT_PATTERN.split(htmlBlock);
        List<String> pages = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                pages.add(trimmed);
            }
        }
        return pages;
    }

    /** 提取第一个匹配的代码块。 */
    private String extractFirstBlock(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /** 提取所有匹配的代码块。 */
    private List<String> extractAllBlocks(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }
}