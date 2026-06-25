package com.prompt2app.agent.codegen.saver;

import com.prompt2app.agent.model.MultiFileCodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MultiFileCodeFileSaverTemplate} 单元测试。
 *
 * <p>核心覆盖（方案 A，2026-06-26 task）：孤儿文件名注释（无 DOCTYPE）被 filter 丢弃，
 * 不再生成空壳 HTML 文件。
 */
class MultiFileCodeFileSaverTemplateTest {

    @TempDir
    Path tmpDir;

    private MultiFileCodeFileSaverTemplate saver() {
        return new MultiFileCodeFileSaverTemplate(tmpDir.toString());
    }

    private File[] htmlFiles() {
        // saveCode 用 appId 拼 multi_file_<appId> 子目录，html 文件在子目录里
        File[] subDirs = tmpDir.toFile().listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) return null;
        return subDirs[0].listFiles((d, n) -> n.endsWith(".html"));
    }

    /** 只统计非 index.html 的 html（saver 兜底会多生成 index.html） */
    private int nonIndexHtmlCount() {
        File[] htmls = htmlFiles();
        if (htmls == null) return 0;
        int count = 0;
        for (File f : htmls) {
            if (!f.getName().equals("index.html")) count++;
        }
        return count;
    }

    private boolean indexHtmlExists() {
        File[] subDirs = tmpDir.toFile().listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) return false;
        return new File(subDirs[0], "index.html").exists();
    }

    @Test
    void orphanCommentsFiltered_onlyRealPagesSaved() {
        // 模拟本次 bug 的 LLM 输出：4 个孤儿注释 + 4 个 DOCTYPE 页
        String htmlCode = """
                <!-- index.html -->
                <!-- about.html -->
                <!-- bestiary.html -->
                <!-- creature.html -->
                <!DOCTYPE html>
                <html><head><title>关于</title></head><body>关于页内容</body></html>
                <!DOCTYPE html>
                <html><head><title>图鉴</title></head><body>图鉴页内容</body></html>
                <!DOCTYPE html>
                <html><head><title>详情</title></head><body>详情页内容</body></html>
                <!DOCTYPE html>
                <html><head><title>首页</title></head><body>首页内容</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        saver().saveCode(result, 100001L);

        File[] htmls = htmlFiles();
        assertNotNull(htmls);
        // 4 个真实页（孤儿注释被 filter）+ 1 个兜底 index.html = 5
        assertEquals(4, nonIndexHtmlCount(), "应只保存 4 个真实页（非 index.html），不含空壳");
        assertTrue(indexHtmlExists(), "应兜底生成 index.html");

        // 确认没有 19-22 字节的空壳
        for (File f : htmls) {
            long size = f.length();
            assertTrue(size > 50, f.getName() + " 太小（" + size + "B），可能是空壳");
        }
    }

    @Test
    void singlePage_savedAsOneFile() {
        String htmlCode = """
                <!DOCTYPE html>
                <html><head><title>单页</title></head><body>内容</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        saver().saveCode(result, 100002L);

        File[] htmls = htmlFiles();
        assertNotNull(htmls);
        assertEquals(1, nonIndexHtmlCount(), "1 个真实页");
        assertTrue(indexHtmlExists(), "兜底 index.html（从单页复制）");
    }

    @Test
    void multiPageWithFileSeparator_realPagesOnly() {
        // FILE_SEPARATOR 路径：parser 注入的（parser 改用 COMPLETE_PAGE_PATTERN 后，
        // 每段都含 DOCTYPE + 可选注释前缀，不会再有孤儿注释段）
        String htmlCode = """
                <!-- ===== FILE_SEPARATOR ===== -->
                <!-- page1.html -->
                <!DOCTYPE html>
                <html><head><title>页一</title></head><body>内容一</body></html>
                <!-- ===== FILE_SEPARATOR ===== -->
                <!DOCTYPE html>
                <html><head><title>页二</title></head><body>内容二</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        saver().saveCode(result, 100003L);

        File[] htmls = htmlFiles();
        assertNotNull(htmls);
        assertEquals(2, nonIndexHtmlCount(), "应存 2 个真实页");
        assertTrue(indexHtmlExists(), "兜底 index.html");
    }

    @Test
    void onlyOrphanComments_throwsOrSavesNothing() {
        // 只有孤儿注释，无任何 DOCTYPE
        String htmlCode = """
                <!-- index.html -->
                <!-- about.html -->
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);

        // validateInput 应抛异常（htmlCode 非空但 splitHtmlPages 返回空 → saveFiles 写 0 个 html）
        // 实际行为：saveFiles 不抛异常但写 0 个 html 文件（css/js 仍写）
        // 这里验证：不应有空壳 html 产生
        MultiFileCodeResult withCss = new MultiFileCodeResult();
        withCss.setHtmlCode(htmlCode);
        withCss.setCssCode("body{}");
        withCss.setJsCode("console.log(1)");

        saver().saveCode(withCss, 100004L);

        File[] htmls = htmlFiles();
        if (htmls != null) {
            assertEquals(0, htmls.length, "不应有任何空壳 html 文件");
        }
    }

    @Test
    void ensureIndexHtml_generatedAfterSave() {
        // MULTI_FILE 产物文件名是 <title> 提取的中文，没有 index.html
        // saver 落盘后应兜底生成 index.html（预览 + 部署共用）
        String htmlCode = """
                <!DOCTYPE html>
                <html><head><title>关于</title></head><body>关于页</body></html>
                <!DOCTYPE html>
                <html><head><title>首页</title></head><body>首页内容</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);
        result.setCssCode("body{}");
        result.setJsCode("console.log(1)");

        saver().saveCode(result, 100005L);

        File[] subDirs = tmpDir.toFile().listFiles(File::isDirectory);
        assertNotNull(subDirs);
        File indexFile = new File(subDirs[0], "index.html");
        assertTrue(indexFile.exists(), "saver 落盘后应兜底生成 index.html");
        // 应复制"首页"标题页（文件名含"首页"）
        String content = readContent(indexFile);
        assertTrue(content.contains("首页内容"), "index.html 应是'首页'页的内容");
    }

    private String readContent(File f) {
        try {
            return java.nio.file.Files.readString(f.toPath());
        } catch (Exception e) {
            return "";
        }
    }

    @Test
    void commentAttachedToDocType_fileNameFromComment() {
        // 注释紧贴 DOCTYPE（本次 bug 的 LLM 输出格式）
        // 修复前：split 拆开注释和 DOCTYPE → 注释被丢 → 文件名从 title 提取中文（如"原神角色图鉴.html"）
        // 修复后：COMPLETE_PAGE_PATTERN 让注释跟着 DOCTYPE → 文件名从注释提取（如"genshin.html"）
        String htmlCode = """
                <!-- index.html -->
                <!DOCTYPE html>
                <html><head><title>米哈游角色图鉴 | 首页</title></head><body>首页</body></html>
                <!-- genshin.html -->
                <!DOCTYPE html>
                <html><head><title>原神 · 角色图鉴</title></head><body>原神</body></html>
                <!-- honkai3.html -->
                <!DOCTYPE html>
                <html><head><title>崩坏3 · 角色图鉴</title></head><body>崩坏3</body></html>
                """;
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(htmlCode);
        result.setCssCode("body{}");
        result.setJsCode("console.log(1)");

        saver().saveCode(result, 100006L);

        File[] subDirs = tmpDir.toFile().listFiles(File::isDirectory);
        assertNotNull(subDirs);
        File dir = subDirs[0];
        String[] htmlNames = dir.list((d, n) -> n.endsWith(".html"));

        assertNotNull(htmlNames);
        // 应有 3 个真实页 + 1 个兜底 index.html = 4 个
        // 关键：应有 genshin.html 和 honkai3.html（从注释提取），不是中文名
        java.util.List<String> names = java.util.Arrays.asList(htmlNames);
        assertTrue(names.contains("index.html"), "应有 index.html（注释提取或兜底）");
        assertTrue(names.contains("genshin.html"), "应有 genshin.html（从注释提取，非中文 title）");
        assertTrue(names.contains("honkai3.html"), "应有 honkai3.html（从注释提取，非中文 title）");
        // 不应有中文 title 名
        for (String n : names) {
            assertFalse(n.contains("原神") || n.contains("崩坏"),
                    "不应有中文 title 文件名: " + n);
        }
    }
}
