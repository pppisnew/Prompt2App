package com.prompt2app.agent.codegen.saver;

import cn.hutool.core.util.StrUtil;
import com.prompt2app.agent.model.MultiFileCodeResult;
import com.prompt2app.infra.exception.BusinessException;
import com.prompt2app.infra.exception.ErrorCode;
import com.prompt2app.app.model.enums.CodeGenTypeEnum;

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
        // 拆分多个 HTML 页面
        String[] htmlPages = htmlCode.split(FILE_SEPARATOR);
        if (htmlPages.length > 1) {
            // 多页站：按 <title> 内容生成文件名
            for (int i = 0; i < htmlPages.length; i++) {
                String page = htmlPages[i].trim();
                if (page.isEmpty()) continue;
                String fileName = resolveHtmlFileName(page, i);
                writeToFile(baseDirPath, fileName, page);
            }
        } else {
            // 单页：保存为 index.html
            writeToFile(baseDirPath, "index.html", htmlCode);
        }
        // 保存 CSS 文件
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        // 保存 JavaScript 文件
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }

    /** 从 <title> 标签提取文件名，提取失败则用 page_0.html / page_1.html ... */
    private String resolveHtmlFileName(String htmlContent, int index) {
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